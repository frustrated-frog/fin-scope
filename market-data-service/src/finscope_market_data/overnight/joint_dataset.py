"""Shared, point-in-time minute panel. Labels and features have separate cutoffs."""
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date
import hashlib
import json

import numpy as np

from finscope_market_data.forecast.trading_calendar import previous_session, next_session
from finscope_market_data.overnight.engine import build_samples, features, group_bars
from finscope_market_data.overnight.session_features import session_features

PROTOCOL = 'overnight-joint-v1'
MIN_SYMBOLS = 20
TRAIN_DAYS = 80
CALIBRATION_DAYS = 20
TEST_DAYS = 20
MAX_DAYS = 240
FEATURES = ('INTRADAY_RETURN', 'LAST_15M_RETURN', 'DAY_HIGH', 'DAY_LOW', 'VOLATILITY',
            'LAST_15M_AMOUNT_SHARE', 'AMOUNT_WEIGHTED_DEVIATION', 'POSITIVE_BAR_SHARE',
            'OPEN_GAP', 'PREVIOUS_OVERNIGHT', 'PREVIOUS_INTRADAY', 'MEAN_OVERNIGHT_5',
            'MEAN_INTRADAY_5', 'RELATIVE_AMOUNT_5', 'POOL_PREVIOUS_RETURN',
            'POOL_PREVIOUS_UP_SHARE', 'POOL_PREVIOUS_DISPERSION', 'RELATIVE_PREVIOUS_RETURN', 'GEM')


@dataclass(frozen=True)
class JointProfile:
    mode: str
    cutoff: str
    cost_bps: float

    @property
    def key(self):
        return f'{self.mode}|{self.cutoff}|{self.cost_bps:g}'

    def dump(self):
        return {'mode': self.mode, 'cutoff': self.cutoff, 'costBps': self.cost_bps, 'key': self.key}


def profiles(context):
    return [JointProfile('TAIL_ENTRY', cutoff, context['tailCostBps']) for cutoff in ('14:30', '14:45')] + [
        JointProfile('AFTER_CLOSE_HOLDING', '15:00', context['holdingCostBps'])]


def contexts(daily):
    return {day: {'count': len(values), 'mean': float(np.mean(values)),
                  'upShare': float(np.mean(np.asarray(values) > 0)), 'dispersion': float(np.std(values))}
            for day, values in daily.items() if len(values) >= MIN_SYMBOLS}


def extend_features(local, context, code):
    return local + [context['mean'], context['upShare'], context['dispersion'],
                    local[10] - context['mean'], float(code.startswith('3'))]


def current_features(grouped, signal_day, cutoff, code, pool_context):
    local = session_features(grouped, signal_day, cutoff)
    previous = previous_session(signal_day)
    context = pool_context.get(str(previous))
    if local is None or context is None:
        return None
    return extend_features(local, context, code)


def build_panel(store, members, profile, through):
    rows, daily, quality = [], defaultdict(list), Counter()
    # Process one stock at a time; do not hold 120 years of minute objects in RAM.
    for code in sorted(set(members)):
        grouped = group_bars(store.bars(code, through))
        for day, bars in grouped.items():
            value = features(bars, '15:00')
            if value is not None and day < through.date():
                daily[str(day)].append(value[0])
        local = {}
        for day in grouped:
            value = session_features(grouped, day, profile.cutoff)
            if value is not None:
                local[str(day)] = value
        samples = build_samples(grouped, profile, through)
        for target, values in samples.items():
            for row in values:
                x = local.get(row['signalDate'])
                if x is None:
                    quality['missingPastSessions'] += 1
                    continue
                signal_day = date.fromisoformat(row['signalDate'])
                following = next_session(signal_day)
                gap = grouped[following]['09:35'].open / grouped[signal_day]['15:00'].close - 1 if (
                    '09:35' in grouped[following] and '15:00' in grouped[signal_day]) else None
                # A conservative anomaly screen, not a verified corporate-action feed.
                bound = .21 if code.startswith('3') else .11
                if gap is None or abs(gap) > bound or abs(x[8]) > bound:
                    quality['priceDiscontinuity'] += 1
                    continue
                rows.append({**row, 'instrumentCode': code, 'target': target, 'features': x})
    context = contexts(daily)
    panel = []
    for row in rows:
        previous = str(previous_session(date.fromisoformat(row['signalDate'])))
        if previous not in context:
            quality['missingPoolContext'] += 1
            continue
        panel.append({**row, 'features': extend_features(row['features'], context[previous], row['instrumentCode'])})
    panel.sort(key=lambda row: (row['signalDate'], row['instrumentCode'], row['target']))
    digest = hashlib.sha256(json.dumps(panel, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()
    return panel, context, {'fingerprint': digest, 'rows': len(panel), 'quality': dict(quality),
                           'symbolCount': len({row['instrumentCode'] for row in panel}),
                           'dayCount': len({row['signalDate'] for row in panel})}


def split_dates(rows, cutoff):
    """Whole dates, purged labels, independent calibration, untouched diagnostic test."""
    matured = [row for row in rows if row['exitAt'] < cutoff]
    dates = sorted({row['signalDate'] for row in matured})[-MAX_DAYS:]
    if len(dates) < TRAIN_DAYS + CALIBRATION_DAYS + TEST_DAYS + 2:
        return None
    test_start = dates[-TEST_DAYS]
    calibration_start = dates[-TEST_DAYS - CALIBRATION_DAYS - 1]
    train = [row for row in matured if dates[0] <= row['signalDate'] < calibration_start
             and row['exitAt'][:10] < calibration_start]
    calibration = [row for row in matured if calibration_start <= row['signalDate'] < test_start
                   and row['exitAt'][:10] < test_start]
    test = [row for row in matured if row['signalDate'] >= test_start]
    if (len({row['signalDate'] for row in train}) < TRAIN_DAYS
            or len({row['signalDate'] for row in calibration}) < CALIBRATION_DAYS
            or min(len({row['instrumentCode'] for row in part}) for part in (train, calibration, test)) < MIN_SYMBOLS):
        return None
    return train, calibration, test


def date_weights(rows):
    counts = Counter(row['signalDate'] for row in rows)
    return np.asarray([1. / counts[row['signalDate']] for row in rows])
