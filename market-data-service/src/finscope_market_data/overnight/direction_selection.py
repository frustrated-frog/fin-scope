"""Predeclared direction selection, using only the purged selection window."""
import numpy as np

from finscope_market_data.overnight.joint_dataset import date_weights

POLICY = 'DIRECTION_BALANCED_V1'
PROTOCOL = 'overnight-context-direction-v2'


def metrics(probabilities, rows):
    y = np.array([row['actualReturn'] > 0 for row in rows])
    p = np.asarray(probabilities)
    weights = date_weights(rows)
    recalls = [float(np.average((p[y == value] >= .5) == value, weights=weights[y == value]))
               if np.any(y == value) else None for value in (False, True)]
    return {'accuracy': float(np.average((p >= .5) == y, weights=weights)),
            'balancedAccuracy': float(np.mean(recalls)) if None not in recalls else None,
            'notUpRecall': recalls[0], 'upRecall': recalls[1],
            'brier': float(np.average((p - y) ** 2, weights=weights)),
            'predictedUpRate': float(np.average(p >= .5, weights=weights))}


def select_direction(probabilities, rows):
    scores = {name: metrics(p, rows) for name, p in probabilities.items()}
    prior = scores['PRIOR']
    dates = sorted({row['signalDate'] for row in rows})
    windows = [set(values) for values in np.array_split(dates, 3)]
    eligible = []
    for name, score in scores.items():
        wins = 0
        for days in windows:
            indices = [i for i, row in enumerate(rows) if row['signalDate'] in days]
            if not indices:
                continue
            part = [rows[i] for i in indices]
            current = metrics(np.asarray(probabilities[name])[indices], part)
            baseline = metrics(np.asarray(probabilities['PRIOR'])[indices], part)
            if (current['balancedAccuracy'] is not None and current['balancedAccuracy'] > .5
                    and current['accuracy'] > baseline['accuracy']):
                wins += 1
        # A majority-class guess must not win on accuracy alone. Probability
        # quality remains a constraint; it is not the direction objective.
        passed = (name != 'PRIOR' and score['balancedAccuracy'] is not None
                  and score['balancedAccuracy'] > .5 and min(score['upRecall'], score['notUpRecall']) >= .35
                  and score['accuracy'] > prior['accuracy'] and score['brier'] <= prior['brier'] + .01
                  and wins >= 2)
        score.update(subwindowWins=wins, eligible=passed)
        if passed:
            eligible.append(name)
    selected = max(eligible, key=lambda name: (scores[name]['balancedAccuracy'], scores[name]['accuracy'],
                   -scores[name]['brier'], -list(probabilities).index(name))) if eligible else 'PRIOR'
    return selected, {'policy': POLICY, 'candidates': scores,
                      'reason': 'DIRECTION_ADVANTAGE' if eligible else 'NO_SELECTION_ADVANTAGE'}
