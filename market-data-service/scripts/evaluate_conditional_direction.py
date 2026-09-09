"""Fixed-input conditional next-day direction experiment; never publishes production probabilities."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

import numpy as np
from threadpoolctl import threadpool_limits

from evaluate_rolling_direction import load_frozen_inputs, source_fingerprint
from finscope_market_data.forecast.conditional_direction import daily_market_data
from finscope_market_data.forecast.conditional_experiment import (
    CONDITIONAL_VERSION, conditional_rollout, select_conditional_method,
)
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.joint_training import temporal_split


def emit(value):
    print(json.dumps(value, ensure_ascii=False), flush=True)


def load_base_oof(path, rows):
    with gzip.open(path, 'rt') as stream:
        records = [json.loads(line) for line in stream]
    if not records:
        raise ValueError('基础 OOF 为空')
    start = records[0]['signalDate']
    expected = tuple(r for r in rows if r.sample.signal_date >= start)
    if len(records) != len(expected):
        raise ValueError('基础 OOF 与冻结股票日数量不一致')
    for record, row in zip(records, expected):
        if ((record['code'], record['signalDate'], record['exitDate']) !=
                (row.code, row.sample.signal_date, row.sample.exit_date)
                or record['returnValue'] != row.sample.net_return or record['positive'] != row.sample.positive):
            raise ValueError('基础 OOF 的股票日或目标与冻结输入不一致')
    return start, np.array([r['probabilities']['BASE_TREE:RAW'] for r in records]), np.array([r['prior'] for r in records])


def evaluate(args):
    source = source_fingerprint() + hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    inputs = Path(args.inputs)
    previous = json.loads(Path(args.baseline).read_text())
    if hashlib.sha256(inputs.read_bytes()).hexdigest() != previous['inputFingerprint']:
        raise ValueError('E0 基准与冻结输入指纹不一致')
    data = load_frozen_inputs(inputs, previous['asOfDate'])
    start, base, priors = load_base_oof(previous['files']['predictions'], data.rows)
    with threadpool_limits(limits=1):
        result = conditional_rollout(data.rows, data.feature_codes, base, start, progress=emit)
        split = temporal_split(data.rows)
        selection = select_conditional_method(result, split['selection'][0].sample.signal_date,
                                                split['calibration'][0].sample.signal_date)
        emit(dict(stage='selected', selected=selection['selected'], passed=selection['passed']))
        test_start = split['test'][0].sample.signal_date
        mask = np.array([r.sample.signal_date >= test_start for r in result['rows']])
        rows = tuple(r for r, keep in zip(result['rows'], mask) if keep)
        dates = np.array([r.sample.signal_date for r in rows])
        y = np.array([r.sample.positive for r in rows])
        with np.load(previous['files']['test'], allow_pickle=False) as reference:
            if not (np.array_equal(reference['dates'], dates) and np.array_equal(reference['labels'], y)
                    and np.array_equal(reference['codes'], [r.code for r in rows])):
                raise ValueError('强基准股票日或目标未对齐')
            fixed = reference['FIXED_LEGACY'].copy()
        baselines = dict(FIXED_LEGACY=fixed, ROLLING_RAW=base[mask], PRIOR=priors[mask])
        audits = {key: evaluate_direction(p[mask], y, dates, baselines) for key, p in result['probabilities'].items()}
        selected = audits[selection['selected']]
        periods = []
        for chunk in np.array_split(sorted(set(dates)), 3):
            part = np.isin(dates, chunk)
            audit = evaluate_direction(result['probabilities'][selection['selected']][mask][part], y[part], dates[part],
                                       {key: p[part] for key, p in baselines.items()})
            periods.append(dict(startDate=str(chunk[0]), endDate=str(chunk[-1]), **audit))
        _, daily_q, breadth = daily_market_data(result['marketProbability'][mask, None], y, dates)
        _, daily_prior, _ = daily_market_data(priors[mask, None], y, dates)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    scores = output.with_suffix('.scores.npz')
    np.savez_compressed(scores, **result['probabilities'], marketProbability=result['marketProbability'],
        rankingScore=result['rankingScore'], batchIds=result['batchIds'],
        dates=np.array([r.sample.signal_date for r in result['rows']]), codes=np.array([r.code for r in result['rows']]),
        exits=np.array([r.sample.exit_date for r in result['rows']]), labels=np.array([r.sample.positive for r in result['rows']]))
    if source != source_fingerprint() + hashlib.sha256(Path(__file__).read_bytes()).hexdigest():
        raise RuntimeError('实验期间源码发生变化，请重放冻结输入')
    summary = dict(version=CONDITIONAL_VERSION, evidenceKind='RETROSPECTIVE', productionEligible=False,
        asOfDate=previous['asOfDate'], inputFingerprint=previous['inputFingerprint'], sourceFingerprint=source,
        universeCount=len({r.code for r in data.rows}), selection=selection, directionEvaluation=selected,
        testAblation=audits, testAblationRole='DIAGNOSTIC_ONLY_NOT_SELECTION', periods=periods,
        testStart=test_start, testEnd=max(r.sample.exit_date for r in rows),
        marketBreadthMse=float(np.mean((daily_q[:, 0]-breadth)**2)),
        marketPriorMse=float(np.mean((daily_prior[:, 0]-breadth)**2)),
        batches=result['batches'], files=dict(scores=str(scores), inputs=str(inputs), baseline=args.baseline),
        limitations=['已参与研发的固定缓存股票池，不是无存活偏差的全 A 股',
                     '预选方法固定，但历史测试期已经看过；不自动替换线上概率',
                     '没有新增行业历史或分钟信息；仅检验已有信息的条件组合'])
    output.write_text(json.dumps(summary, ensure_ascii=False, allow_nan=False))
    emit(dict(stage='complete', output=str(output), selected=selection['selected'],
        metrics={key: {m: audit[m] for m in ('accuracy', 'balancedAccuracy', 'brierScore', 'auc')} for key, audit in audits.items()},
        marketBreadthMse=summary['marketBreadthMse'], marketPriorMse=summary['marketPriorMse']))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--output', required=True)
    evaluate(parser.parse_args())
