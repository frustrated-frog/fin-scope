"""Point-in-time next-close panel with compact Alpha158-inspired price/volume factors."""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, replace
import hashlib
import json
import math
from typing import Mapping, Sequence

import numpy as np

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.features import FEATURE_CODES, ForecastSample, _validated_bars, current_features
from finscope_market_data.forecast.next_session import build_close_samples
from finscope_market_data.forecast.panel_features import augment_cross_sectional_features

HISTORY_LIMIT = 1061
EXTRA_FEATURE_CODES = (
    'CANDLE_BODY', 'CANDLE_RANGE', 'UPPER_SHADOW', 'LOWER_SHADOW',
    'RSV_20', 'RSV_60', 'PRICE_VOLUME_CORRELATION_20',
    'RETURN_VOLUME_CORRELATION_20', 'VOLUME_VARIATION_20', 'RSI_20',
)


@dataclass(frozen=True)
class JointRow:
    code: str
    sample: ForecastSample


@dataclass(frozen=True)
class JointDataset:
    as_of: str
    rows: tuple[JointRow, ...]
    current_features_by_code: dict[str, tuple[float, ...]]
    history_fingerprints: dict[str, str]
    feature_codes: tuple[str, ...]


def history_fingerprint(bars: Sequence[DailyBar]) -> str:
    effective = sorted(bars, key=lambda bar: bar.trade_date)[-HISTORY_LIMIT:]
    payload = [bar.model_dump(mode='json') for bar in effective]
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def _correlation(left: np.ndarray, right: np.ndarray) -> float:
    if np.std(left) < 1e-12 or np.std(right) < 1e-12:
        return 0.0
    return float(np.corrcoef(left, right)[0, 1])


def _extra_features(bars: Sequence[DailyBar], index: int) -> tuple[float, ...]:
    bar = bars[index]
    window = bars[index - 19:index + 1]
    closes = np.array([b.close for b in window], dtype=float)
    volumes = np.array([b.volume for b in window], dtype=float)
    previous = np.array([b.close for b in bars[index - 20:index]], dtype=float)
    returns = closes / previous - 1
    total_move = float(np.abs(returns).sum())
    rsv = []
    for length in (20, 60):
        selected = bars[index - length + 1:index + 1]
        low = min(b.low for b in selected)
        high = max(b.high for b in selected)
        rsv.append((bar.close - low) / (high - low) if high > low else 0.5)
    values = (
        (bar.close - bar.open) / bar.open,
        (bar.high - bar.low) / bar.open,
        (bar.high - max(bar.open, bar.close)) / bar.open,
        (min(bar.open, bar.close) - bar.low) / bar.open,
        *rsv, _correlation(closes, volumes), _correlation(returns, volumes),
        float(np.std(volumes) / np.mean(volumes)),
        float(np.maximum(returns, 0).sum()) / total_move if total_move > 0 else 0.5,
    )
    if not all(math.isfinite(value) for value in values):
        raise ValueError('价量特征包含非有限值')
    return values


def build_joint_dataset(
    histories: Mapping[str, Sequence[DailyBar]], *, as_of: str,
    market_bars: Sequence[DailyBar] = (), minimum_cross_section: int = 20,
) -> JointDataset:
    market = tuple(bar for bar in market_bars if bar.trade_date <= as_of)
    eligible = {}
    for code, bars in sorted(histories.items()):
        past = sorted((bar for bar in bars if bar.trade_date <= as_of), key=lambda bar: bar.trade_date)[-HISTORY_LIMIT:]
        if len(past) < 61:
            continue
        try:
            eligible[code] = _validated_bars(past)
        except ValueError:
            continue
    dates = sorted({bar.trade_date for bar in market} if market else {
        bar.trade_date for bars in eligible.values() for bar in bars
    })
    next_date = dict(zip(dates, dates[1:]))
    samples_by_code = {}
    valid_labels = set()
    current = {}
    fingerprints = {}
    for code, bars in eligible.items():
        context = build_aligned_context(bars, market_bars=market)
        indices = {bar.trade_date: index for index, bar in enumerate(bars)}
        samples = build_close_samples(bars, context)
        valid_labels.update((code, sample.signal_date) for sample in samples
                            if next_date.get(sample.signal_date) == sample.exit_date)
        # Include the final observable close even when its future label is unknown.
        # Cross-sectional feature membership must not reveal tomorrow's suspension.
        samples.append(ForecastSample(bars[-1].trade_date, bars[-1].trade_date,
                                      bars[-1].trade_date, current_features(bars, context), 0.0))
        samples_by_code[code] = tuple(
            replace(sample, features=(*sample.features, *_extra_features(bars, indices[sample.signal_date])))
            for sample in samples
        )
        if bars[-1].trade_date == as_of:
            current[code] = (*current_features(bars, context), *_extra_features(bars, len(bars) - 1))
            fingerprints[code] = history_fingerprint(bars)
    enriched, current, cross_codes = augment_cross_sectional_features(
        samples_by_code, current, minimum_cross_section=minimum_cross_section,
    )
    if not current or not enriched:
        raise ValueError('有效次日预测截面不足')
    rows = tuple(sorted((JointRow(code, sample) for code, samples in enriched.items() for sample in samples
                         if (code, sample.signal_date) in valid_labels),
                        key=lambda row: (row.sample.signal_date, row.code)))
    counts = Counter(row.sample.signal_date for row in rows)
    rows = tuple(row for row in rows if counts[row.sample.signal_date] >= minimum_cross_section)
    if not rows:
        raise ValueError('可验证次日标签的截面不足')
    return JointDataset(as_of, rows, current, fingerprints, (*FEATURE_CODES, *EXTRA_FEATURE_CODES, *cross_codes))
