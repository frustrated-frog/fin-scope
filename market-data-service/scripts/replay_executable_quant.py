#!/usr/bin/env python3
"""Submit a frozen bundle to Java and save the exact result plus a readable account report."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen


def cell(value: object) -> str:
    return str(value).replace('|', '\\|').replace('\n', ' ')


def render(report: dict) -> str:
    protocol = report['protocol']
    account = report['account']
    equity = account['equityCurve']
    lines = [
        '# 可执行策略账户回放', '',
        '**这是模拟回放，不是实际成交；示例输入为合成数据时，不代表策略盈利证据。**', '',
        f"- 协议：{cell(protocol['version'])}",
        f"- 引擎：{cell(report['engineVersion'])}",
        f"- 输入指纹：`{report['inputFingerprint']}`",
        f"- 初始资金：{protocol['initialCapital']:.2f}",
        f"- 期末资产：{equity[-1]['totalAsset']:.2f}",
        f"- 账户净收益：{(equity[-1]['totalAsset'] / protocol['initialCapital'] - 1):.4%}",
        f"- 最大回撤：{account['metrics']['maxDrawdown']:.4%}",
        f"- 成交费用合计（滑点已计入成交价格）：{sum(t['fee'] for t in account['trades']):.2f}",
        '', '## 目标持仓', '', '| 信号日期 | 标的 | 目标权重 |', '|---|---|---:|',
    ]
    for day, targets in report['targetWeights'].items():
        if not targets:
            lines.append(f'| {day} | 目标为空，卖出后保留现金 | 0% |')
        for code, weight in targets.items():
            lines.append(f'| {day} | {code} | {weight:.2%} |')
    lines += ['', '## 订单与成交', '',
              '| 信号日期 | 执行日期 | 标的 | 方向 | 请求股数 | 成交股数 | 费用 | 结果 |',
              '|---|---|---|---|---:|---:|---:|---|']
    reasons = {'FILLED': '全部成交', 'PARTIAL_BUDGET': '预算限制，部分成交',
               'OPEN_BLOCKED': '开盘交易受阻', 'NO_LOT_BUDGET': '预算不足一手',
               'COST_EXCEEDS_CASH': '现金不足以支付卖出费用'}
    for order in report['orders']:
        values = [order['signalDate'], order['tradeDate'], order['instrumentCode'],
                  {'BUY': '买入', 'SELL': '卖出'}[order['side']], order['requestedQuantity'],
                  order['filledQuantity'], f"{order['fee']:.2f}", reasons.get(order['reason'], order['reason'])]
        lines.append('| ' + ' | '.join(cell(value) for value in values) + ' |')
    lines += ['', '## 每日账户', '', '| 日期 | 现金 | 持仓市值 | 总资产 | 净值 |', '|---|---:|---:|---:|---:|']
    for point in equity:
        lines.append(f"| {point['tradeDate']} | {point['cash']:.2f} | "
                     f"{point['totalAsset'] - point['cash']:.2f} | {point['totalAsset']:.2f} | {point['portfolioNav']:.6f} |")
    lines += ['', '## 边界与警告', '']
    lines.extend('- ' + cell(warning) for warning in account['warnings'])
    lines += ['', '完整候选、训练截止时间、数据证据、成交价格与持仓明细见同名 JSON；其中 frozenInput 可直接重放。', '']
    return '\n'.join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--input', type=Path, help='Frozen input JSON to send to the Java API')
    source.add_argument('--result', type=Path, help='Render an existing Java result without another replay')
    parser.add_argument('--api', default='http://localhost:8080/api/quant/executable-replays')
    parser.add_argument('--output', type=Path, required=True, help='New output prefix; .json and .md are written')
    args = parser.parse_args()
    json_path = Path(str(args.output) + '.json')
    markdown_path = Path(str(args.output) + '.md')
    if json_path.exists() or markdown_path.exists():
        parser.error('Output exists; use a new prefix to preserve the frozen result')
    if args.result:
        report = json.loads(args.result.read_text())
    else:
        request = Request(args.api, data=args.input.read_bytes(), headers={'Content-Type': 'application/json'}, method='POST')
        with urlopen(request, timeout=120) as response:
            envelope = json.load(response)
        if not envelope.get('success'):
            raise RuntimeError(envelope.get('message', 'Replay failed'))
        report = envelope['data']
    markdown = render(report)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    with json_path.open('x') as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write('\n')
    with markdown_path.open('x') as output:
        output.write(markdown)
    print(markdown_path)


if __name__ == '__main__':
    main()
