"""Read-only E0 calibration x retraining experiment. Historical results remain retrospective."""
import argparse
from dataclasses import asdict
import gzip
import hashlib
import json
from pathlib import Path

import numpy as np
from threadpoolctl import threadpool_limits

from evaluate_general_next_session import ReadOnlyHistory
from finscope_market_data.forecast.adaptive_classifiers import date_weights, fit_candidates
from finscope_market_data.forecast.calibration import PlattCalibrator
from finscope_market_data.forecast.direction_calibration import fit_direction_calibration
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.industry_features import IndustryMembership
from finscope_market_data.forecast.frozen_panel import load_frozen_panel, save_frozen_panel
from finscope_market_data.forecast.joint_dataset import build_joint_dataset
from finscope_market_data.forecast.joint_training import PARAMETERS, temporal_split
from finscope_market_data.forecast.rolling_direction import (
    ROLLING_VERSION, calibrated_array, rolling_forecasts, select_direction_method,
)
from finscope_market_data.forecast.training_universe import load_training_universe


def emit(value):
    print(json.dumps(value, ensure_ascii=False), flush=True)


def load_frozen_inputs(path, as_of):
    return load_frozen_panel(path, as_of)


def source_fingerprint():
    paths = sorted((Path(__file__).parents[1] / 'src/finscope_market_data/forecast').glob('*.py')) + [Path(__file__)]
    return hashlib.sha256(b''.join(p.name.encode() + p.read_bytes() for p in paths)).hexdigest()


def evaluate(args):
    source_hash = source_fingerprint()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if args.frozen_inputs:
        dataset = load_frozen_inputs(args.frozen_inputs, args.as_of)
        codes = sorted({r.code for r in dataset.rows})
    else:
        store = ReadOnlyHistory(args.snapshots)
        try:
            histories = load_training_universe(store, as_of=args.as_of, max_symbols=args.max_symbols)
            benchmark = store.daily_history_as_of('SH:000300', args.as_of)
        finally:
            store.connection.close()
        records = json.loads(Path(args.memberships).read_text()) if Path(args.memberships).exists() else []
        memberships = tuple(IndustryMembership(r['industry'], r['available_on'], tuple(r['codes'])) for r in records)
        emit(dict(stage='dataset', stocks=len(histories)))
        dataset = build_joint_dataset(histories, as_of=args.as_of, market_bars=benchmark, memberships=memberships)
        codes = sorted(histories)
    split = temporal_split(dataset.rows)
    selection_start = split['selection'][0].sample.signal_date
    selection_end = split['calibration'][0].sample.signal_date
    test_start = split['test'][0].sample.signal_date
    days = sorted({r.sample.signal_date for r in dataset.rows})
    start = days[days.index(selection_start)-60]
    # Store actual frozen model inputs, not just a fingerprint of a mutable cache.
    inputs = output.with_suffix('.inputs.npz')
    if dataset.observable_rows:
        save_frozen_panel(inputs, dataset)
    else:
        # Legacy replay remains explicitly label-only; never claim panel v2.
        np.savez_compressed(inputs, features=np.array([r.sample.features for r in dataset.rows]),
            returns=np.array([r.sample.net_return for r in dataset.rows]), codes=np.array([r.code for r in dataset.rows]),
            signalDates=np.array([r.sample.signal_date for r in dataset.rows]),
            exitDates=np.array([r.sample.exit_date for r in dataset.rows]), featureCodes=np.array(dataset.feature_codes))
    with threadpool_limits(limits=1):
        rolling = rolling_forecasts(dataset.rows, dataset.feature_codes, PARAMETERS, start, progress=emit)
        selection = select_direction_method(rolling, selection_start, selection_end)
        emit(dict(stage='selected', selected=selection['selected'], passed=selection['passed']))
        code, mode = selection['selected'].split(':')
        fixed = fit_candidates(split['train'], dataset.feature_codes, PARAMETERS, codes=(code,))[code]
        cx = np.array([r.sample.features for r in split['calibration']])
        cy = np.array([r.sample.positive for r in split['calibration']])
        vx = np.array([r.sample.features for r in split['test']])
        cp, raw = fixed.predict_proba(cx)[:, 1], fixed.predict_proba(vx)[:, 1]
        legacy = PlattCalibrator.fit(cp, cy)
        modern = fit_direction_calibration(cp, cy, date_weights(split['calibration']), mode)
        test_mask = np.array([r.sample.signal_date >= test_start for r in rolling['rows']])
        test_rows = tuple(r for r, keep in zip(rolling['rows'], test_mask) if keep)
        if test_rows != split['test']:
            raise ValueError('固定与滚动测试股票日未对齐')
        cases = dict(FIXED_RAW=raw, FIXED_LEGACY=calibrated_array(legacy, raw), FIXED_NEW=calibrated_array(modern, raw),
            ROLLING_RAW=rolling['probabilities'][f'{code}:RAW'][test_mask],
            ROLLING_LEGACY=rolling['probabilities'][f'{code}:LEGACY'][test_mask],
            ROLLING_NEW=rolling['probabilities'][selection['selected']][test_mask])
        y = np.array([r.sample.positive for r in test_rows])
        dates = [r.sample.signal_date for r in test_rows]
        audits = {key: evaluate_direction(p, y, dates, {'FIXED_LEGACY': cases['FIXED_LEGACY'],
                    'ROLLING_PRIOR': rolling['priors'][test_mask]}) for key, p in cases.items()}
        diagnostic_ablation = {key: evaluate_direction(p[test_mask], y, dates, {'FIXED_LEGACY': cases['FIXED_LEGACY']})
                               for key, p in rolling['probabilities'].items()}
    prediction_path = output.with_suffix('.predictions.jsonl.gz')
    with gzip.open(prediction_path, 'wt', encoding='utf-8') as stream:
        for i, row in enumerate(rolling['rows']):
            record = dict(code=row.code, signalDate=row.sample.signal_date, exitDate=row.sample.exit_date,
                          positive=row.sample.positive, returnValue=row.sample.net_return,
                          batchId=int(rolling['batchIds'][i]), prior=float(rolling['priors'][i]),
                          probabilities={key: float(p[i]) for key, p in rolling['probabilities'].items()})
            stream.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + '\n')
    test_path = output.with_suffix('.test.npz')
    np.savez_compressed(test_path, **cases, labels=y, dates=np.array(dates), codes=np.array([r.code for r in test_rows]))
    if source_fingerprint() != source_hash:
        raise RuntimeError('实验运行期间源码发生变化，请使用冻结输入重新执行')
    summary = dict(version=ROLLING_VERSION, evidenceKind='RETROSPECTIVE', methodFrozenThrough='2026-09-09',
        asOfDate=args.as_of, codes=codes, featureCodes=dataset.feature_codes, sourceFingerprint=source_hash,
        inputFingerprint=hashlib.sha256(inputs.read_bytes()).hexdigest(),
        parameters=PARAMETERS, stepDays=5, trainingWindowDays=505, calibrationWindowDays=60,
        selection=selection, comparisons=audits, testAblation=diagnostic_ablation,
        testAblationRole='DIAGNOSTIC_ONLY_NOT_SELECTION', batches=rolling['batches'],
        fixedTrainingThrough=max(r.sample.exit_date for r in split['train']),
        fixedCalibrationThrough=max(r.sample.exit_date for r in split['calibration']),
        fixedCalibrators=dict(legacy=asdict(legacy), modern=asdict(modern)),
        testStart=test_start, testEnd=max(r.sample.exit_date for r in test_rows),
        flatSampleCount=int(sum(r.sample.net_return == 0 for r in test_rows)),
        files=dict(inputs=str(inputs), predictions=str(prediction_path), test=str(test_path)),
        limitations=['已看过历史期，只是回溯研究；不能自动升级生产概率',
                     '缓存股票池存在存活和选择偏差，不是历史全 A 股',
                     '固定模型使用独立留出校准，滚动模型使用同族历史 OOF；两者校准数据分布不同',
                     '校准只混合同族同更新规则；模型参数随时间变化，映射稳定性仍需前瞻验证'])
    output.write_text(json.dumps(summary, ensure_ascii=False, allow_nan=False), encoding='utf-8')
    emit(dict(stage='complete', output=str(output), selected=selection['selected'],
              metrics={k: {m: v[m] for m in ('accuracy', 'balancedAccuracy', 'brierScore', 'predictedUpRate', 'auc')}
                       for k, v in audits.items()}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--as-of', required=True)
    parser.add_argument('--snapshots', default='data/market-data-snapshots.db')
    parser.add_argument('--memberships', default='data/quant/industry-membership-history.json')
    parser.add_argument('--max-symbols', type=int, default=240)
    parser.add_argument('--output', required=True)
    parser.add_argument('--frozen-inputs', help='重放已保存的特征矩阵，绕过可变行情缓存')
    evaluate(parser.parse_args())
