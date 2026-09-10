"""Descriptive diagnostics of frozen forecasts, never features or model selection."""
import numpy as np

from finscope_market_data.forecast.direction_evaluation import probability_diagnostics


def attribute_direction_errors(probabilities, baseline, labels, dates):
    p, base = np.asarray(probabilities, dtype=float), np.asarray(baseline, dtype=float)
    y, dates = np.asarray(labels), np.asarray(dates)
    probability_diagnostics(p, y, dates)
    probability_diagnostics(base, y, dates)
    if p.shape != base.shape:
        raise ValueError('归因概率必须对应同一组股票日')
    days = []
    for day in sorted(set(dates)):
        mask = dates == day
        candidate, reference, target = p[mask], base[mask], y[mask]
        hit, base_hit = (candidate >= .5) == target, (reference >= .5) == target
        corrected, broken = hit & ~base_hit, ~hit & base_hit
        decomposition = {}
        for key, values in (('candidate', candidate), ('baseline', reference)):
            centered = (values - values.mean()) - (target - target.mean())
            decomposition[key] = dict(brierScore=float(np.mean((values-target)**2)),
                dailyMeanError=float((values.mean()-target.mean())**2), centeredError=float(np.mean(centered**2)))
        days.append(dict(signalDate=str(day), sampleCount=int(mask.sum()),
            correctedCount=int(corrected.sum()), brokenCount=int(broken.sum()),
            unchangedCount=int((hit == base_hit).sum()),
            upToNonUpCount=int(((reference >= .5) & (candidate < .5)).sum()),
            nonUpToUpCount=int(((reference < .5) & (candidate >= .5)).sum()),
            accuracy=float(hit.mean()), baselineAccuracy=float(base_hit.mean()),
            accuracyDifference=float(corrected.mean()-broken.mean()),
            observedUpRate=float(target.mean()), predictedUpRate=float((candidate >= .5).mean()),
            baselinePredictedUpRate=float((reference >= .5).mean()),
            probabilityMean=float(candidate.mean()), baselineProbabilityMean=float(reference.mean()),
            probabilityQuantiles=np.quantile(candidate, [.05, .25, .5, .75, .95]).tolist(),
            brierDecomposition=decomposition))
    average = lambda field: float(np.mean([day[field]/day['sampleCount'] for day in days]))
    return dict(weighting='EQUAL_DATE_THEN_STOCK', sampleCount=len(p), dayCount=len(days),
        correctedCount=sum(day['correctedCount'] for day in days),
        brokenCount=sum(day['brokenCount'] for day in days),
        unchangedCount=sum(day['unchangedCount'] for day in days),
        correctedRate=average('correctedCount'), brokenRate=average('brokenCount'),
        accuracyDifference=float(np.mean([day['accuracyDifference'] for day in days])),
        brierDecomposition={key: {field: float(np.mean([day['brierDecomposition'][key][field] for day in days]))
            for field in ('brierScore', 'dailyMeanError', 'centeredError')} for key in ('candidate', 'baseline')},
        interpretation='描述性恒等分解；居中误差包含标签噪声，不等于可被模型消除的个股误差', days=days)
