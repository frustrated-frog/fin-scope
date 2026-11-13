"""Date-balanced descriptive up/down detection metrics."""
import numpy as np

from finscope_market_data.overnight.joint_dataset import date_weights


def detection(rows):
    if not rows:
        return None
    weights = date_weights(rows)
    p = np.array([r['probability'] for r in rows]) >= .5
    y = np.array([r['actualUp'] for r in rows], dtype=bool)
    hit = p & y
    return {'sampleCount': len(rows), 'dayCount': len({r['signalDate'] for r in rows}),
        'predictedUpCount': int(p.sum()), 'actualUpCount': int(y.sum()), 'trueUpCount': int(hit.sum()),
        'missedUpCount': int((~p & y).sum()), 'falseUpCount': int((p & ~y).sum()),
        'upPrecision': float(weights[hit].sum() / weights[p].sum()) if p.any() else None,
        'upRecall': float(weights[hit].sum() / weights[y].sum()) if y.any() else None,
        'predictedUpRate': float(np.average(p, weights=weights)),
        'accuracy': float(np.average(p == y, weights=weights)),
        'alwaysNotUpAccuracy': float(np.average(~y, weights=weights)),
        'weighting': 'EQUAL_DATE_THEN_STOCK'}
