from __future__ import annotations

from collections import defaultdict
from typing import Mapping, Sequence

from finscope_market_data.forecast.features import ForecastSample


CROSS_SECTIONAL_FEATURE_CODES = (
    "CROSS_MOMENTUM_SHORT_RANK",
    "CROSS_MOMENTUM_MEDIUM_RANK",
    "CROSS_MOMENTUM_LONG_RANK",
    "CROSS_LOW_VOLATILITY_RANK",
    "CROSS_ACTIVITY_RANK",
    "CROSS_LOW_DOWNSIDE_RANK",
    "CROSS_NEAR_HIGH_RANK",
    "CROSS_UP_BREADTH",
)


def augment_cross_sectional_features(
    samples_by_code: Mapping[str, Sequence[ForecastSample]],
    current_features_by_code: Mapping[str, Sequence[float]],
    *,
    minimum_cross_section: int = 20,
) -> tuple[
    dict[str, tuple[ForecastSample, ...]],
    dict[str, tuple[float, ...]],
    tuple[str, ...],
]:
    if minimum_cross_section < 2:
        raise ValueError("截面最少股票数必须大于一")
    by_date: dict[str, list[tuple[str, ForecastSample]]] = defaultdict(list)
    for code, samples in sorted(samples_by_code.items()):
        for sample in samples:
            by_date[sample.signal_date].append((code, sample))
    result: dict[str, list[ForecastSample]] = defaultdict(list)
    for signal_date in sorted(by_date):
        rows = sorted(by_date[signal_date], key=lambda item: item[0])
        if len(rows) < minimum_cross_section:
            continue
        extras = _cross_features({code: sample.features for code, sample in rows})
        for code, sample in rows:
            result[code].append(ForecastSample(
                signal_date=sample.signal_date,
                entry_date=sample.entry_date,
                exit_date=sample.exit_date,
                features=(*sample.features, *extras[code]),
                net_return=sample.net_return,
            ))
    current = {
        code: (*tuple(float(value) for value in features), *extras)
        for code, extras in _cross_features(current_features_by_code).items()
        for features in (current_features_by_code[code],)
    } if len(current_features_by_code) >= minimum_cross_section else {}
    return (
        {code: tuple(values) for code, values in sorted(result.items())},
        current,
        CROSS_SECTIONAL_FEATURE_CODES,
    )


def _cross_features(
    features_by_code: Mapping[str, Sequence[float]],
) -> dict[str, tuple[float, ...]]:
    if not features_by_code:
        return {}
    dimensions = {len(values) for values in features_by_code.values()}
    if len(dimensions) != 1 or next(iter(dimensions)) == 0:
        raise ValueError("截面特征维度不一致")
    dimension_count = next(iter(dimensions))
    selected = (
        0,
        min(1, dimension_count - 1),
        min(2, dimension_count - 1),
        min(5, dimension_count - 1),
        min(6, dimension_count - 1),
        min(9, dimension_count - 1),
        min(10, dimension_count - 1),
    )
    ranks = [_percentile_ranks(features_by_code, dimension) for dimension in selected]
    breadth = sum(float(values[0]) > 0 for values in features_by_code.values()) / len(features_by_code)
    result: dict[str, tuple[float, ...]] = {}
    for code in sorted(features_by_code):
        result[code] = (
            ranks[0][code],
            ranks[1][code],
            ranks[2][code],
            1.0 - ranks[3][code],
            ranks[4][code],
            1.0 - ranks[5][code],
            ranks[6][code],
            breadth,
        )
    return result


def _percentile_ranks(
    values_by_code: Mapping[str, Sequence[float]],
    dimension: int,
) -> dict[str, float]:
    ordered = sorted(
        ((float(values[dimension]), code) for code, values in values_by_code.items()),
        key=lambda item: (item[0], item[1]),
    )
    denominator = max(1, len(ordered) - 1)
    result: dict[str, float] = {}
    start = 0
    while start < len(ordered):
        end = start + 1
        while end < len(ordered) and ordered[end][0] == ordered[start][0]:
            end += 1
        midpoint = ((start + end - 1) / 2.0) / denominator
        for _, code in ordered[start:end]:
            result[code] = midpoint
        start = end
    return result
