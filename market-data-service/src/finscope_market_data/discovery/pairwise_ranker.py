from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations
import math
import statistics
from typing import Sequence

from sklearn.linear_model import LogisticRegression


MINIMUM_DATE_COUNT = 20
MAX_PAIRS_PER_DATE = 300


@dataclass(frozen=True)
class PairwiseRankingObservation:
    group_id: str
    instrument_code: str
    features: tuple[float, ...]
    actual_net_return: float


@dataclass(frozen=True)
class PairwiseRankingReport:
    status: str
    training_date_count: int
    calibration_date_count: int
    locked_date_count: int
    observation_count: int
    pair_count: int
    pairwise_accuracy: float | None
    rank_ic: float | None
    top_k: int
    top_k_average_return: float | None
    admitted_pool_average_return: float | None
    top_k_excess_return: float | None
    feature_weights: tuple[float, ...]
    method: str
    reason: str | None = None


def evaluate_pairwise_ranker(
    observations: Sequence[PairwiseRankingObservation],
    *,
    top_k: int = 3,
) -> PairwiseRankingReport:
    if top_k < 1:
        raise ValueError("pairwise 排序 Top-K 必须为正整数")
    ordered = tuple(sorted(
        observations,
        key=lambda item: (item.group_id, item.instrument_code),
    ))
    _validate(ordered)
    grouped = _grouped(ordered)
    dates = tuple(sorted(grouped))
    if len(dates) < MINIMUM_DATE_COUNT:
        return _insufficient(len(ordered), top_k, "独立到期批次少于 20 个")
    development_end = max(12, int(len(dates) * 0.60))
    calibration_end = max(development_end + 4, int(len(dates) * 0.80))
    calibration_end = min(calibration_end, len(dates) - 4)
    training_dates = dates[:development_end]
    calibration_dates = dates[development_end:calibration_end]
    locked_dates = dates[calibration_end:]
    training_pairs = _pairs(grouped, training_dates)
    if not training_pairs:
        return _insufficient(len(ordered), top_k, "训练日期内没有可比较的收益差")
    estimator = LogisticRegression(
        C=0.5,
        solver="lbfgs",
        max_iter=500,
        random_state=20260830,
    )
    estimator.fit(
        [features for features, _ in training_pairs],
        [label for _, label in training_pairs],
    )
    weights = tuple(float(value) for value in estimator.coef_[0])
    locked_pairs = _pairs(grouped, locked_dates)
    pairwise_accuracy = (
        statistics.fmean(
            bool(estimator.predict([features])[0]) == bool(label)
            for features, label in locked_pairs
        )
        if locked_pairs else None
    )
    rank_values: list[float] = []
    top_returns: list[float] = []
    pool_returns: list[float] = []
    excess_values: list[float] = []
    for current_date in locked_dates:
        values = grouped[current_date]
        scores = [_score(item.features, weights) for item in values]
        returns = [item.actual_net_return for item in values]
        correlation = _spearman(scores, returns)
        if correlation is not None:
            rank_values.append(correlation)
        ranked = sorted(
            zip(values, scores),
            key=lambda pair: (-pair[1], pair[0].instrument_code),
        )
        selected = [item.actual_net_return for item, _ in ranked[:top_k]]
        pool = statistics.fmean(returns)
        top = statistics.fmean(selected)
        top_returns.extend(selected)
        pool_returns.extend(returns)
        excess_values.append(top - pool)
    rank_ic = statistics.fmean(rank_values) if rank_values else None
    top_excess = statistics.fmean(excess_values) if excess_values else None
    promotion_ready = (
        len(locked_dates) >= 10
        and pairwise_accuracy is not None
        and pairwise_accuracy >= 0.55
        and rank_ic is not None
        and rank_ic > 0.05
        and top_excess is not None
        and top_excess > 0.0
    )
    return PairwiseRankingReport(
        status="PROMOTION_REVIEW" if promotion_ready else "SHADOW_EVALUATING",
        training_date_count=len(training_dates),
        calibration_date_count=len(calibration_dates),
        locked_date_count=len(locked_dates),
        observation_count=len(ordered),
        pair_count=len(training_pairs),
        pairwise_accuracy=pairwise_accuracy,
        rank_ic=rank_ic,
        top_k=top_k,
        top_k_average_return=(
            statistics.fmean(top_returns) if top_returns else None
        ),
        admitted_pool_average_return=(
            statistics.fmean(pool_returns) if pool_returns else None
        ),
        top_k_excess_return=top_excess,
        feature_weights=weights,
        method="DATE_GROUPED_PAIRWISE_LOGISTIC_V1",
        reason=(
            "锁定区排序与 Top-K 超额同时通过，等待人工晋升复核"
            if promotion_ready
            else "排序挑战者继续影子评测，尚未同时越过全部门槛"
        ),
    )


def _validate(values: Sequence[PairwiseRankingObservation]) -> None:
    if not values:
        return
    dimensions = {len(item.features) for item in values}
    if len(dimensions) != 1 or next(iter(dimensions)) < 1:
        raise ValueError("pairwise 排序特征维度必须一致且非空")
    keys = [(item.group_id, item.instrument_code) for item in values]
    if len(keys) != len(set(keys)):
        raise ValueError("pairwise 排序观察存在重复股票日期")
    if any(
        not math.isfinite(item.actual_net_return)
        or any(not math.isfinite(value) for value in item.features)
        for item in values
    ):
        raise ValueError("pairwise 排序观察必须为有限数值")


def _grouped(
    values: Sequence[PairwiseRankingObservation],
) -> dict[str, tuple[PairwiseRankingObservation, ...]]:
    result: dict[str, list[PairwiseRankingObservation]] = {}
    for item in values:
        result.setdefault(item.group_id, []).append(item)
    return {
        key: tuple(sorted(items, key=lambda item: item.instrument_code))
        for key, items in result.items()
        if len(items) >= 2
    }


def _pairs(
    grouped: dict[str, tuple[PairwiseRankingObservation, ...]],
    dates: Sequence[str],
) -> list[tuple[list[float], int]]:
    result: list[tuple[list[float], int]] = []
    for current_date in dates:
        current: list[tuple[list[float], int]] = []
        for left, right in combinations(grouped.get(current_date, ()), 2):
            if abs(left.actual_net_return - right.actual_net_return) < 1e-12:
                continue
            difference = [
                left_value - right_value
                for left_value, right_value in zip(left.features, right.features)
            ]
            label = int(left.actual_net_return > right.actual_net_return)
            current.append((difference, label))
            current.append(([-value for value in difference], 1 - label))
        result.extend(current[:MAX_PAIRS_PER_DATE])
    return result


def _score(features: Sequence[float], weights: Sequence[float]) -> float:
    return sum(feature * weight for feature, weight in zip(features, weights))


def _spearman(left: Sequence[float], right: Sequence[float]) -> float | None:
    if len(left) < 2 or len(left) != len(right):
        return None
    left_ranks = _ranks(left)
    right_ranks = _ranks(right)
    left_mean = statistics.fmean(left_ranks)
    right_mean = statistics.fmean(right_ranks)
    numerator = sum(
        (left_value - left_mean) * (right_value - right_mean)
        for left_value, right_value in zip(left_ranks, right_ranks)
    )
    left_scale = math.sqrt(sum((value - left_mean) ** 2 for value in left_ranks))
    right_scale = math.sqrt(sum((value - right_mean) ** 2 for value in right_ranks))
    if left_scale < 1e-12 or right_scale < 1e-12:
        return None
    return numerator / (left_scale * right_scale)


def _ranks(values: Sequence[float]) -> list[float]:
    ordered = sorted(enumerate(values), key=lambda item: (item[1], item[0]))
    result = [0.0 for _ in values]
    start = 0
    while start < len(ordered):
        end = start + 1
        while end < len(ordered) and ordered[end][1] == ordered[start][1]:
            end += 1
        rank = (start + end - 1) / 2.0 + 1.0
        for position in range(start, end):
            result[ordered[position][0]] = rank
        start = end
    return result


def _insufficient(
    observation_count: int, top_k: int, reason: str
) -> PairwiseRankingReport:
    return PairwiseRankingReport(
        status="SHADOW_ACCUMULATING",
        training_date_count=0,
        calibration_date_count=0,
        locked_date_count=0,
        observation_count=observation_count,
        pair_count=0,
        pairwise_accuracy=None,
        rank_ic=None,
        top_k=top_k,
        top_k_average_return=None,
        admitted_pool_average_return=None,
        top_k_excess_return=None,
        feature_weights=(),
        method="DATE_GROUPED_PAIRWISE_LOGISTIC_V1",
        reason=reason,
    )
