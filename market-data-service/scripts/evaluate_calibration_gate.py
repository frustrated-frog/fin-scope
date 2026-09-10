"""Replay the predeclared mature-OOF gate on frozen industry experiment scores."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from finscope_market_data.forecast.calibration_gate import GATE_VERSION, replay_calibration_gate
from finscope_market_data.forecast.direction_evaluation import evaluate_direction


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scores', type=Path, required=True)
    parser.add_argument('--summary', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    summary = json.loads(args.summary.read_text())
    protocol = summary['protocol']
    result = dict(version=GATE_VERSION, evidence='RETROSPECTIVE', productionEligible=False,
        protocol=dict(historyDays=60, windowCount=3, minimumWindowWins=2, threshold=.5,
            testStart=protocol['testStart'], testEnd=protocol['testEnd'],
            directionRule='ACCURACY_STRICT_GAIN_AND_BA_NONDEGRADE_AND_BRIER_NONDEGRADE',
            probabilityRule='BRIER_STRICT_GAIN', history='PREVIOUSLY_ISSUED_MATURE_OOF_ONLY'),
        scoresFingerprint=hashlib.sha256(args.scores.read_bytes()).hexdigest(),
        summaryFingerprint=hashlib.sha256(args.summary.read_bytes()).hexdigest(),
        sourceFingerprint=hashlib.sha256(Path(__file__).read_bytes() +
            (Path(__file__).resolve().parents[1] / 'src/finscope_market_data/forecast/calibration_gate.py').read_bytes()).hexdigest(),
        limitations=['规则在已检查过的历史区间回放，尚无独立前向验证',
                     'GATED_PROBABILITY 的阈值命中率仅作诊断，不作为方向输出',
                     '历史行业可用时间假设与原实验样本选择局限仍然存在'], arms={})
    with np.load(args.scores, allow_pickle=False) as values:
        dates, labels = values['dates'], values['labels']
        if len(set(zip(dates, values['codes']))) != len(dates):
            raise ValueError('冻结预测包含重复股票日期')
        selected = (labels >= 0) & (dates >= protocol['testStart']) & (dates <= protocol['testEnd'])
        days = np.unique(dates[selected])
        if len(days) != 180:
            raise ValueError('固定实验需要原始 180 日期窗口')
        for arm in ('D', 'DS'):
            raw, calibrated = values[arm+'_RAW'], values[arm+'_INTERCEPT']
            replay = replay_calibration_gate(raw, calibrated, labels, dates, values['exits'], values['batchIds'])
            outputs = dict(RAW=raw, INTERCEPT=calibrated, GATED_DIRECTION=replay['direction'],
                           GATED_PROBABILITY=replay['probability'])
            evaluations = []
            for period in [days, *np.array_split(days, 3)]:
                mask = selected & np.isin(dates, period)
                baselines = dict(RAW=raw[mask], INTERCEPT=calibrated[mask],
                                 ALWAYS_UP=np.ones(mask.sum()), ALWAYS_NONUP=np.zeros(mask.sum()))
                metrics = {name: evaluate_direction(p[mask], labels[mask], dates[mask], baselines)
                           for name, p in outputs.items()}
                evaluations.append(dict(start=str(period[0]), end=str(period[-1]), metrics=metrics))
            result['arms'][arm] = dict(evaluations=evaluations, decisions=replay['decisions'])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as output:
        json.dump(result, output, ensure_ascii=False, indent=2)
    for arm, data in result['arms'].items():
        print(arm, {name: round(metric['accuracy']*100, 4)
                    for name, metric in data['evaluations'][0]['metrics'].items()})


if __name__ == '__main__':
    main()
