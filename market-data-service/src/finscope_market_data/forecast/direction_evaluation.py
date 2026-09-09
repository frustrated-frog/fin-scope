"""Date-balanced absolute-direction evaluation with paired moving-block uncertainty."""
import numpy as np
from sklearn.metrics import roc_auc_score


def probability_diagnostics(probabilities, labels, dates):
    p, y, dates = np.asarray(probabilities, dtype=float), np.asarray(labels), np.asarray(dates)
    if (p.ndim != 1 or p.shape != y.shape or p.shape != dates.shape or not len(p)
            or not np.all(np.isfinite(p)) or np.any((p < 0) | (p > 1))
            or not np.all(np.isin(y, [0, 1]))):
        raise ValueError('概率诊断输入无效')
    days, inverse, counts = np.unique(dates, return_inverse=True, return_counts=True)
    weights = 1. / counts[inverse] / len(days)
    aucs = []
    for index in range(len(days)):
        mask = inverse == index
        if len(np.unique(y[mask])) == 2:
            aucs.append(float(roc_auc_score(y[mask], p[mask])))
    bounded = np.clip(p, 1e-6, 1 - 1e-6)
    # Weighted inverse empirical CDF: dates, then stocks, have equal mass.
    order = np.argsort(p, kind='stable')
    cumulative = np.cumsum(weights[order])
    quantiles = {f'p{q:02d}': float(p[order[min(len(p)-1, np.searchsorted(cumulative, q/100))]])
                 for q in (5, 25, 50, 75, 95)}
    return dict(predictedUpRate=float(weights @ (p >= .5)), probabilityQuantiles=quantiles,
        auc=float(roc_auc_score(y, p, sample_weight=weights)) if len(np.unique(y)) == 2 else None,
        crossSectionAuc=float(np.mean(aucs)) if aucs else None, crossSectionAucDayCount=len(aucs),
        logLoss=float(-weights @ (y * np.log(bounded) + (1-y) * np.log1p(-bounded))))


def evaluate_direction(probabilities, labels, dates, baselines, regimes=None):
    p, y, dates = np.asarray(probabilities, dtype=float), np.asarray(labels, dtype=float), np.asarray(dates)
    if not len(p) or len(p) != len(y) or len(p) != len(dates) or not baselines:
        raise ValueError('方向评价需要等长非空的概率、标签、日期和基准')
    if not np.all(np.isfinite(p)) or np.any((p < 0) | (p > 1)) or not np.all(np.isin(y, [0, 1])):
        raise ValueError('方向评价概率或标签无效')
    days, inverse, counts = np.unique(dates, return_inverse=True, return_counts=True)
    weights = 1 / counts[inverse] / len(days)
    hit = (p >= .5) == y
    errors = (p - y) ** 2
    average = lambda values: float(np.sum(weights * values))
    positive, negative = y == 1, y == 0
    balanced = ((float(np.sum(weights[positive] * hit[positive]) / np.sum(weights[positive]))
                 + float(np.sum(weights[negative] * hit[negative]) / np.sum(weights[negative]))) / 2
                if np.any(positive) and np.any(negative) else None)
    daily = lambda values: np.bincount(inverse, weights=values, minlength=len(days)) / counts
    rng = np.random.default_rng(20260909)
    block = min(5, len(days))
    starts = rng.integers(0, len(days), size=(2000, int(np.ceil(len(days) / block))))
    bootstrap = ((starts[:, :, None] + np.arange(block)) % len(days)).reshape(2000, -1)[:, :len(days)]
    comparisons = {}
    alpha = .05 / len(baselines)
    for code, values in baselines.items():
        baseline = np.asarray(values, dtype=float)
        if baseline.shape != p.shape or not np.all(np.isfinite(baseline)) or np.any((baseline < 0) | (baseline > 1)):
            raise ValueError('方向基准概率无效')
        base_hit = (baseline >= .5) == y
        brier_difference = daily(errors - (baseline - y) ** 2)
        accuracy_difference = daily(hit.astype(float) - base_hit.astype(float))
        brier_ci = np.quantile(np.mean(brier_difference[bootstrap], axis=1), [alpha / 2, 1 - alpha / 2])
        accuracy_ci = np.quantile(np.mean(accuracy_difference[bootstrap], axis=1), [alpha / 2, 1 - alpha / 2])
        comparisons[code] = dict(brierScore=average((baseline - y) ** 2), accuracy=average(base_hit),
            brierDifference=average(errors - (baseline - y) ** 2), accuracyDifference=average(hit.astype(float) - base_hit.astype(float)),
            brierDifferenceLower=float(brier_ci[0]), brierDifferenceUpper=float(brier_ci[1]),
            accuracyDifferenceLower=float(accuracy_ci[0]), accuracyDifferenceUpper=float(accuracy_ci[1]))
    confident = np.abs(p - .5) >= .1 - 1e-12
    coverage = average(confident)
    by_regime = {}
    if regimes is not None:
        regimes = np.asarray(regimes)
        if len(regimes) != len(p):
            raise ValueError('市场状态与评价样本不等长')
        for regime in sorted(set(regimes)):
            mask = regimes == regime
            scale = float(np.sum(weights[mask]))
            by_regime[str(regime)] = dict(sampleCount=int(mask.sum()), dayCount=len(set(dates[mask])),
                accuracy=float(np.sum(weights[mask] * hit[mask]) / scale), brierScore=float(np.sum(weights[mask] * errors[mask]) / scale))
    eligible = bool(len(days) >= 60 and balanced is not None and balanced > .5
                    and all(v['brierDifferenceUpper'] < 0 and v['accuracyDifferenceLower'] > 0 for v in comparisons.values()))
    return dict(task='NEXT_CLOSE_DIRECTION', sampleCount=len(p), dayCount=len(days), accuracy=average(hit),
        balancedAccuracy=balanced, brierScore=average(errors), observedUpRate=average(y),
        **probability_diagnostics(p, y, dates),
        highConfidence=dict(threshold=.6, coverage=coverage, accuracy=average(hit & confident) / coverage if coverage else None),
        comparisons=comparisons, byRegime=by_regime, eligible=eligible, blockDays=block,
        confidenceLevel=1-alpha, weighting='EQUAL_DATE_THEN_STOCK',
        reason='方向与概率均通过日期分块比较' if eligible else '方向或概率尚未同时形成可信优势，保留预测供观察')
