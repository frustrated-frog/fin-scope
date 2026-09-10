"""Choose direction and probability outputs using mature, previously issued forecasts."""
import numpy as np


GATE_VERSION = 'mature-oof-calibration-gate-v1'


def _metrics(p, y, dates):
    days, inverse, counts = np.unique(dates, return_inverse=True, return_counts=True)
    w = 1. / counts[inverse] / len(days)
    hit = (p >= .5) == y
    recalls = [float(w[y == label] @ hit[y == label] / w[y == label].sum())
               for label in (0, 1) if np.any(y == label)]
    return dict(accuracy=float(w @ hit), brier=float(w @ ((p-y)**2)),
                balancedAccuracy=float(np.mean(recalls)) if len(recalls) == 2 else None)


def select_calibration_outputs(raw, calibrated, labels, dates):
    raw, calibrated = np.asarray(raw, dtype=float), np.asarray(calibrated, dtype=float)
    y, dates = np.asarray(labels), np.asarray(dates)
    if (raw.ndim != 1 or raw.shape != calibrated.shape or raw.shape != y.shape or raw.shape != dates.shape
            or not np.all(np.isfinite(raw)) or not np.all(np.isfinite(calibrated))
            or np.any((raw < 0) | (raw > 1)) or np.any((calibrated < 0) | (calibrated > 1))
            or not np.all(np.isin(y, [0, 1]))):
        raise ValueError('校准选择需要对齐且有效的成熟 OOF 概率与标签')
    days = np.unique(dates)[-60:]
    result = dict(version=GATE_VERSION, directionSource='RAW', probabilitySource='RAW',
                  matureDayCount=len(days), reason='INSUFFICIENT_MATURE_DAYS')
    if len(days) < 60:
        return result
    mask = np.isin(dates, days)
    a, b = _metrics(raw[mask], y[mask], dates[mask]), _metrics(calibrated[mask], y[mask], dates[mask])
    windows = []
    for chunk in np.array_split(days, 3):
        selected = np.isin(dates, chunk)
        windows.append((_metrics(raw[selected], y[selected], dates[selected]),
                        _metrics(calibrated[selected], y[selected], dates[selected])))
    direction_wins = sum(after['accuracy'] > before['accuracy'] for before, after in windows)
    probability_wins = sum(after['brier'] < before['brier'] for before, after in windows)
    if (b['accuracy'] > a['accuracy'] and b['balancedAccuracy'] is not None
            and a['balancedAccuracy'] is not None and b['balancedAccuracy'] >= a['balancedAccuracy']
            and b['brier'] <= a['brier'] and direction_wins >= 2):
        result['directionSource'] = 'INTERCEPT'
    if b['brier'] < a['brier'] and probability_wins >= 2:
        result['probabilitySource'] = 'INTERCEPT'
    result.update(reason='MATURE_OOF_COMPARISON', raw=a, intercept=b,
                  directionWindowWins=direction_wins, probabilityWindowWins=probability_wins,
                  historyStart=str(days[0]), historyThrough=str(days[-1]))
    return result


def replay_calibration_gate(raw, calibrated, labels, dates, exits, batch_ids):
    """Reuse frozen outputs; neither future labels nor refitted historical calibration enter selection."""
    raw, calibrated, y, dates, exits, batch_ids = map(np.asarray,
        (raw, calibrated, labels, dates, exits, batch_ids))
    if raw.ndim != 1 or not len(raw) or any(values.shape != raw.shape for values in (calibrated, y, dates, exits, batch_ids)):
        raise ValueError('回放输入未对齐')
    if (not np.all(np.isin(y, [-1, 0, 1])) or np.any(batch_ids < 0)
            or np.any(batch_ids != batch_ids.astype(int))
            or np.any(dates[1:] < dates[:-1]) or np.any(batch_ids[1:] < batch_ids[:-1])
            or np.any((y >= 0) & (exits <= dates))):
        raise ValueError('回放日期、成熟标签或批次顺序无效')
    select_calibration_outputs(raw, calibrated, np.zeros(len(raw)), dates)
    direction, probability, decisions = raw.copy(), raw.copy(), []
    for batch_id in np.unique(batch_ids):
        mask = batch_ids == batch_id
        first = str(min(dates[mask]))
        mature = (y >= 0) & (dates < first) & (exits != '') & (exits < first)
        decision = select_calibration_outputs(raw[mature], calibrated[mature], y[mature], dates[mature])
        direction[mask] = (calibrated if decision['directionSource'] == 'INTERCEPT' else raw)[mask]
        probability[mask] = (calibrated if decision['probabilitySource'] == 'INTERCEPT' else raw)[mask]
        decisions.append(dict(batchId=int(batch_id), startDate=first,
            maturityThrough=str(max(exits[mature])) if np.any(mature) else None, **decision))
    return dict(direction=direction, probability=probability, decisions=decisions)
