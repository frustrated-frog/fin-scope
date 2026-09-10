"""Audit saved E1 scores without refitting or replacing historical experiments."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.stats import rankdata, spearmanr

from evaluate_rolling_direction import load_frozen_inputs
from finscope_market_data.forecast.conditional_experiment import select_conditional_method
from finscope_market_data.forecast.direction_error_attribution import attribute_direction_errors
from finscope_market_data.forecast.direction_evaluation import evaluate_direction, probability_diagnostics


def fingerprint(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def audit(args):
    summary = json.loads(Path(args.experiment).read_text())
    baseline = json.loads(Path(summary['files']['baseline']).read_text())
    inputs = summary['files']['inputs']
    if fingerprint(inputs) != summary['inputFingerprint'] or baseline['inputFingerprint'] != summary['inputFingerprint']:
        raise ValueError('实验与基准的冻结输入不一致')
    data = load_frozen_inputs(Path(inputs), summary['asOfDate'])
    with np.load(summary['files']['scores'], allow_pickle=False) as saved:
        scores = {key: saved[key].copy() for key in saved.files}
    first_signal = str(min(scores['dates']))
    rows = tuple(r for r in data.rows if r.sample.signal_date >= first_signal)
    if not (np.array_equal(scores['codes'], [r.code for r in rows])
            and np.array_equal(scores['dates'], [r.sample.signal_date for r in rows])
            and np.array_equal(scores['exits'], [r.sample.exit_date for r in rows])
            and np.array_equal(scores['labels'], [r.sample.positive for r in rows])):
        raise ValueError('保存的预测与冻结股票日、标签不一致')
    probabilities = {key: scores[key] for key in summary['testAblation']}
    selection = select_conditional_method(dict(rows=rows, probabilities=probabilities),
        summary['selection']['startDate'], summary['selection']['endExclusive'])
    mask = scores['dates'] >= summary['testStart']
    dates, y = scores['dates'][mask], scores['labels'][mask]
    with np.load(baseline['files']['test'], allow_pickle=False) as test:
        if not all(np.array_equal(test[key], scores[key][mask]) for key in ('codes', 'dates', 'labels')):
            raise ValueError('基准测试股票日不一致')
        fixed = test['FIXED_LEGACY'].copy()
    references = dict(FIXED_LEGACY=fixed, ROLLING_RAW=scores['BASE'][mask])
    comparisons = {}
    for key, p in probabilities.items():
        comparisons[key] = {}
        for name, reference in references.items():
            values = attribute_direction_errors(p[mask], reference, y, dates)
            original = evaluate_direction(p[mask], y, dates, {name: reference})
            if not np.isclose(values['accuracyDifference'], original['comparisons'][name]['accuracyDifference'], atol=1e-12):
                raise ValueError('归因与原评价的准确率差异不一致')
            comparisons[key][name] = values
    returns = np.array([r.sample.net_return for r in rows])[mask]
    rank, raw = scores['rankingScore'][mask], scores['BASE'][mask]
    rank_ic, correlations = [], []
    for day in sorted(set(dates)):
        part = dates == day
        if len(set(rank[part])) > 1 and len(set(returns[part])) > 1:
            rank_ic.append(float(spearmanr(rank[part], returns[part]).statistic))
        if len(set(rank[part])) > 1 and len(set(raw[part])) > 1:
            correlations.append(float(spearmanr(rank[part], raw[part]).statistic))
    ranking = probability_diagnostics((rankdata(rank) - .5) / len(rank), y, dates)
    result = dict(evidenceKind='RETROSPECTIVE', productionEligible=False,
        originalSelected=summary['selection']['selected'], revisedSelection=selection,
        sourceExperiment=str(args.experiment), sourceFingerprint=fingerprint(args.experiment),
        scoresFingerprint=fingerprint(summary['files']['scores']), inputFingerprint=summary['inputFingerprint'],
        rankingDiagnostics=dict(crossSectionAuc=ranking['crossSectionAuc'],
            rankIc=float(np.mean(rank_ic)) if rank_ic else None, rankIcDayCount=len(rank_ic),
            baseSpearman=float(np.mean(correlations)) if correlations else None), comparisons=comparisons,
        limitations=['旧冻结文件只保存有标签股票，不能证明缺标签股票的预测截面不变',
                     '筛选规则修复后的历史重评分不是新增样本外准确率证据'])
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, allow_nan=False))
    print(json.dumps(dict(output=str(output), originalSelected=result['originalSelected'],
        revisedSelected=selection['selected'], ranking=result['rankingDiagnostics'],
        selectedAttribution={name: {key: values[key] for key in ('sampleCount', 'dayCount', 'correctedCount',
            'brokenCount', 'correctedRate', 'brokenRate', 'accuracyDifference', 'brierDecomposition')}
            for name, values in comparisons[result['originalSelected']].items()}), ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--experiment', required=True)
    parser.add_argument('--output', required=True)
    audit(parser.parse_args())
