"""Run the local 1/5-session model comparison using cached histories; no model API needed."""
import argparse
import json
from pathlib import Path

from threadpoolctl import threadpool_limits

from finscope_market_data.forecast.executable_sources import load_research_histories
from finscope_market_data.forecast.multi_horizon import run_multi_horizon
from finscope_market_data.models import DailyBar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshots', type=Path, default=Path('data/market-data-snapshots.db'))
    parser.add_argument('--code', required=True)
    parser.add_argument('--calendar-code', default='000300.SH')
    parser.add_argument('--as-of', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('输出目录已存在，请使用新目录保留原始预测')
    histories = load_research_histories(args.snapshots, {args.code, args.calendar_code}, args.as_of)
    bars = [DailyBar.model_validate(row) for row in histories[args.code]][-1061:]
    calendar = sorted({row['trade_date'] for row in histories[args.calendar_code]})
    if not bars or bars[-1].trade_date != args.as_of or not calendar or calendar[-1] != args.as_of:
        parser.error('个股与参考指数日历必须覆盖指定截止日')
    with threadpool_limits(limits=1):
        result = run_multi_horizon(bars, calendar, instrument_code=args.code)
    args.output.mkdir(parents=True)
    inputs = dict(code=args.code, asOf=args.as_of, calendarCode=args.calendar_code, calendar=calendar,
                  bars=[bar.model_dump(mode='json') for bar in bars])
    (args.output/'input.json').write_text(json.dumps(inputs, ensure_ascii=False, indent=2, allow_nan=False))
    (args.output/'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
    lines = ['# 本地多周期预测报告', '', f"标的：{args.code}；数据截至 {args.as_of}。", '',
             '| 周期 | 当前上涨概率 | 方向准确率 | 旧版准确率 | Brier | 旧版 Brier | 收益 MAE | 区间覆盖 |',
             '|---|---:|---:|---:|---:|---:|---:|---:|']
    for horizon, report in result['horizons'].items():
        audit, current = report['evaluation'], report['current']
        old = audit['comparisons']['LEGACY']
        lines.append(f"| {horizon} 日 | {current['upProbability']:.2%} | {audit['accuracy']:.2%} | "
                     f"{old['accuracy']:.2%} | {audit['brierScore']:.4f} | {old['brierScore']:.4f} | "
                     f"{audit['returnMae']:.2%} | {audit['intervalCoverage']:.2%} |")
    lines += ['', *['- '+warning for warning in result['limitations']], '',
              '完整预测、逐段结果、模型选择与校准记录见 report.json。当前上涨概率不是历史准确率。']
    (args.output/'report.md').write_text('\n'.join(lines)+'\n')
    print(json.dumps(dict(output=str(args.output), modelVersion=result['modelVersion'])))


if __name__ == '__main__':
    main()
