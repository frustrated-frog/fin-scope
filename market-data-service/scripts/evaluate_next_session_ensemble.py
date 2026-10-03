"""Paired rolling replay of v4 and its real v3 incumbent, using read-only caches.

Outputs are retrospective research, never published as live predictions. Freeze
parameters on a separate development period before using this comparison.
"""
import argparse
from contextlib import closing
from dataclasses import replace
from datetime import date
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3

from threadpoolctl import threadpool_limits

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.features import _validated_bars
from finscope_market_data.forecast.next_session import build_close_samples, REFIT_INTERVAL
from finscope_market_data.forecast.next_session_ensemble import MODEL_VERSION, NextSessionEnsemble, RECENT_WEIGHT
from finscope_market_data.forecast.short_term import (
    HALF_LIFE, REGULARIZATION_C, SHORT_TERM_FEATURE_CODES, TRAIN_WINDOW, short_term_features,
)


def load_inputs(path, through, count, skip):
    """Hash order is independent of returns; current-cache selection has survivorship bias."""
    with closing(sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)) as connection:
        connection.execute('BEGIN')

        def read(key):
            row = connection.execute("SELECT payload_json FROM market_data_snapshot "
                                     "WHERE capability='DAILY_BARS' AND symbol_key=?", (key,)).fetchone()
            return [] if row is None else [bar for bar in json.loads(row[0])['data'] if bar['trade_date'] <= through]

        market = read('SH:000300')
        if not market:
            raise ValueError('缺少沪深 300 参考行情')
        keys = [row[0] for row in connection.execute(
            "SELECT symbol_key FROM market_data_snapshot WHERE capability='DAILY_BARS'")
            if row[0].startswith(('SH:6', 'SZ:0', 'SZ:3'))]
        keys.sort(key=lambda key: hashlib.sha256(('next-session-v4' + key).encode()).hexdigest())
        histories, excluded, eligible = [], {}, 0
        for key in keys:
            raw = read(key)
            if len(raw) < 900:
                continue
            try:
                _validated_bars([DailyBar.model_validate(bar) for bar in raw])
            except ValueError as error:
                excluded[key] = str(error)
                continue
            eligible += 1
            if eligible <= skip:
                continue
            histories.append(dict(key=key, bars=raw))
            if len(histories) == count:
                break
        if len(histories) < count:
            raise ValueError('缓存中符合长度和质量条件的股票不足')
        return dict(market=market, histories=histories, excluded=excluded)


def replay(inputs, start, end):
    market = [DailyBar.model_validate(row) for row in inputs['market']]
    observations, refits, excluded = [], [], {}
    for history in inputs['histories']:
        code = history['key']
        bars = _validated_bars([DailyBar.model_validate(row) for row in history['bars']])
        context = build_aligned_context(bars, market_bars=market)
        samples = build_close_samples(bars, context)
        indices = {bar.trade_date: index for index, bar in enumerate(bars)}
        short = [replace(row, features=short_term_features(bars, indices[row.signal_date], context)) for row in samples]
        tested = [index for index, row in enumerate(samples) if start <= row.signal_date <= end]
        if not tested or tested[0] < 240:
            excluded[code] = '比较区间为空或训练历史不足'
            continue
        for offset, index in enumerate(tested):
            sample = samples[index]
            if offset % REFIT_INTERVAL == 0:
                fit = NextSessionEnsemble.fit(samples[:index], short[:index], sample.signal_date)
                refits.append(dict(code=code, cutoff=sample.signal_date, audit=fit.audit))
            probability, expected, lower, upper = fit.predict(sample.features, short[index].features)
            observations.append(dict(code=code, signalDate=sample.signal_date, targetDate=sample.exit_date,
                actualReturn=sample.net_return, positive=int(sample.positive), probability=probability,
                incumbentProbability=fit.incumbent.predict(sample.features)[0],
                recentProbability=fit.recent.predict(short[index].features), prior=fit.incumbent.baseline,
                expectedReturn=expected, lowerReturn=lower, upperReturn=upper))
        print(json.dumps(dict(code=code, sampleCount=len(tested)), ensure_ascii=False), flush=True)
    if not observations:
        raise ValueError('没有可比较的预测样本')
    labels = [row['positive'] for row in observations]
    dates = [row['signalDate'] for row in observations]
    baselines = {'LOCAL_V3': [row['incumbentProbability'] for row in observations],
                 'PRIOR': [row['prior'] for row in observations], 'NOT_UP': [.49] * len(observations)}
    evaluation = evaluate_direction([row['probability'] for row in observations], labels, dates, baselines)
    recent = evaluate_direction([row['recentProbability'] for row in observations], labels, dates, baselines)
    return dict(modelVersion=MODEL_VERSION, evidenceKind='RETROSPECTIVE', evaluation=evaluation,
                recentOnlyEvaluation=recent, universeCount=len({row['code'] for row in observations}),
                excluded=excluded, refits=refits), observations


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshots', type=Path, default=Path('data/market-data-snapshots.db'))
    parser.add_argument('--input', type=Path, help='复用冻结的 input.json.gz 或 JSON；优先于缓存')
    parser.add_argument('--start', required=True)
    parser.add_argument('--end', required=True)
    parser.add_argument('--through', required=True, help='冻结行情截止日，必须覆盖待验证目标日')
    parser.add_argument('--symbols', type=int, default=32)
    parser.add_argument('--skip', type=int, default=0, help='跳过哈希顺序中前 N 个合格股票，用于独立股票组')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for value in (args.start, args.end, args.through):
        date.fromisoformat(value)
    if args.start > args.end or args.end > args.through or not 1 <= args.symbols <= 256 or args.skip < 0:
        parser.error('日期、股票数量或偏移无效')
    if args.output.exists():
        parser.error('输出目录已存在；禁止覆盖已冻结的实验')
    if args.input:
        opener = gzip.open if args.input.suffix == '.gz' else open
        with opener(args.input, 'rt') as handle:
            inputs = json.load(handle)
    else:
        inputs = load_inputs(args.snapshots, args.through, args.symbols, args.skip)
    inputs['market'] = [bar for bar in inputs['market'] if bar['trade_date'] <= args.through]
    for history in inputs['histories']:
        history['bars'] = [bar for bar in history['bars'] if bar['trade_date'] <= args.through]
    payload = json.dumps(inputs, ensure_ascii=False, sort_keys=True, allow_nan=False).encode()
    args.output.mkdir(parents=True)
    (args.output / 'input.json.gz').write_bytes(gzip.compress(payload, mtime=0))
    protocol = dict(modelVersion=MODEL_VERSION, evidenceKind='RETROSPECTIVE', start=args.start, end=args.end,
        through=args.through, inputFingerprint=hashlib.sha256(payload).hexdigest(),
        symbols=[row['key'] for row in inputs['histories']], recentWeight=RECENT_WEIGHT,
        trainWindow=TRAIN_WINDOW, halfLife=HALF_LIFE, regularizationC=REGULARIZATION_C,
        refitInterval=REFIT_INTERVAL, featureCodes=SHORT_TERM_FEATURE_CODES,
        limitations=['当前缓存股票池有幸存者偏差，前复权历史不是当日原始快照',
                     '历史重放不是事先冻结的真实预测，准确率提升不代表未来可盈利',
                     '以收盘到次日收盘为标签，不验证尾盘买入和交易费用',
                     '按日期分块统计不消除研发多次尝试的偏差'])
    (args.output / 'protocol.json').write_text(json.dumps(protocol, ensure_ascii=False, indent=2))
    with threadpool_limits(limits=1):
        report, observations = replay(inputs, args.start, args.end)
    report['protocol'] = protocol
    (args.output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
    with gzip.open(args.output / 'observations.jsonl.gz', 'wt') as handle:
        for row in observations:
            handle.write(json.dumps(row, allow_nan=False) + '\n')
    print(json.dumps(dict(output=str(args.output), evaluation=report['evaluation']), ensure_ascii=False))


if __name__ == '__main__':
    main()
