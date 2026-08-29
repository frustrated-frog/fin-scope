from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations
import math
from statistics import NormalDist
import statistics
from typing import Sequence


MINIMUM_RETURN_COUNT = 30
ANNUALIZATION_DAYS = 252.0
MAX_COMBINATIONS = 70


@dataclass(frozen=True)
class SelectionBiasAuditResult:
    status: str
    verdict: str
    trial_count: int
    return_observation_count: int
    observed_sharpe: float | None
    probabilistic_sharpe_probability: float | None
    deflated_sharpe_probability: float | None
    expected_maximum_sharpe: float | None
    probability_of_backtest_overfitting: float | None
    minimum_track_record_length: int | None
    skewness: float | None
    excess_kurtosis: float | None
    combination_count: int
    method: str
    reason: str | None = None


def audit_selection_bias(
    trial_returns: Sequence[Sequence[float]],
    *,
    evaluated_trial_count: int,
    periods_per_year: float = ANNUALIZATION_DAYS,
) -> SelectionBiasAuditResult:
    if evaluated_trial_count < 1:
        raise ValueError("量化试验数必须为正整数")
    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("年化周期数必须为有限正数")
    normalized = tuple(tuple(float(value) for value in values) for values in trial_returns)
    if any(any(not math.isfinite(value) for value in values) for values in normalized):
        raise ValueError("选择偏差审计收益必须为有限数值")
    if not normalized or min(map(len, normalized)) < MINIMUM_RETURN_COUNT:
        return _insufficient(
            evaluated_trial_count,
            min(map(len, normalized), default=0),
            "选择偏差审计至少需要 30 个样本外收益观察",
        )
    lengths = {len(values) for values in normalized}
    if len(lengths) != 1:
        raise ValueError("选择偏差审计的策略收益序列长度必须一致")
    daily_sharpes = tuple(_sharpe(values) for values in normalized)
    best_index = max(range(len(normalized)), key=lambda index: daily_sharpes[index])
    selected = normalized[best_index]
    daily_sharpe = daily_sharpes[best_index]
    skewness = _skewness(selected)
    excess_kurtosis = _excess_kurtosis(selected)
    standard_error = _sharpe_standard_error(
        daily_sharpe,
        len(selected),
        skewness,
        excess_kurtosis,
    )
    expected_maximum_daily = (
        standard_error * _expected_maximum_standard_normal(evaluated_trial_count)
    )
    probabilistic = _normal_cdf(daily_sharpe / standard_error)
    deflated = _normal_cdf(
        (daily_sharpe - expected_maximum_daily) / standard_error
    )
    pbo, combination_count = _pbo(normalized)
    minimum_length = _minimum_track_record_length(
        daily_sharpe,
        expected_maximum_daily,
        skewness,
        excess_kurtosis,
    )
    verdict = _verdict(deflated, pbo)
    return SelectionBiasAuditResult(
        status="AVAILABLE",
        verdict=verdict,
        trial_count=evaluated_trial_count,
        return_observation_count=len(selected),
        observed_sharpe=daily_sharpe * math.sqrt(periods_per_year),
        probabilistic_sharpe_probability=probabilistic,
        deflated_sharpe_probability=deflated,
        expected_maximum_sharpe=(
            expected_maximum_daily * math.sqrt(periods_per_year)
        ),
        probability_of_backtest_overfitting=pbo,
        minimum_track_record_length=minimum_length,
        skewness=skewness,
        excess_kurtosis=excess_kurtosis,
        combination_count=combination_count,
        method="DEFLATED_SHARPE_CSCV_V1",
        reason=_reason(verdict, pbo),
    )


def _sharpe(values: Sequence[float]) -> float:
    deviation = statistics.stdev(values)
    if deviation < 1e-12:
        return 0.0
    return statistics.fmean(values) / deviation


def _skewness(values: Sequence[float]) -> float:
    mean = statistics.fmean(values)
    deviation = statistics.stdev(values)
    if deviation < 1e-12:
        return 0.0
    count = len(values)
    return (
        count / ((count - 1) * (count - 2))
        * sum(((value - mean) / deviation) ** 3 for value in values)
    )


def _excess_kurtosis(values: Sequence[float]) -> float:
    mean = statistics.fmean(values)
    deviation = statistics.stdev(values)
    if deviation < 1e-12 or len(values) < 4:
        return 0.0
    count = len(values)
    fourth = sum(((value - mean) / deviation) ** 4 for value in values)
    return (
        count * (count + 1) / ((count - 1) * (count - 2) * (count - 3))
        * fourth
        - 3.0 * (count - 1) ** 2 / ((count - 2) * (count - 3))
    )


def _sharpe_standard_error(
    sharpe: float,
    count: int,
    skewness: float,
    excess_kurtosis: float,
) -> float:
    variance = (
        1.0
        - skewness * sharpe
        + (excess_kurtosis + 2.0) * sharpe * sharpe / 4.0
    ) / max(1, count - 1)
    return math.sqrt(max(1e-12, variance))


def _expected_maximum_standard_normal(trial_count: int) -> float:
    if trial_count <= 1:
        return 0.0
    normal = NormalDist()
    euler_gamma = 0.5772156649015329
    first = normal.inv_cdf(1.0 - 1.0 / trial_count)
    second = normal.inv_cdf(1.0 - 1.0 / (trial_count * math.e))
    return (1.0 - euler_gamma) * first + euler_gamma * second


def _normal_cdf(value: float) -> float:
    return min(1.0, max(0.0, NormalDist().cdf(value)))


def _minimum_track_record_length(
    observed_sharpe: float,
    benchmark_sharpe: float,
    skewness: float,
    excess_kurtosis: float,
) -> int:
    edge = observed_sharpe - benchmark_sharpe
    if edge <= 1e-12:
        return 999999
    z_score = NormalDist().inv_cdf(0.95)
    adjustment = max(
        1e-12,
        1.0
        - skewness * observed_sharpe
        + (excess_kurtosis + 2.0) * observed_sharpe ** 2 / 4.0,
    )
    return max(2, math.ceil(1.0 + adjustment * (z_score / edge) ** 2))


def _pbo(
    trials: Sequence[Sequence[float]],
) -> tuple[float | None, int]:
    if len(trials) < 2:
        return None, 0
    count = len(trials[0])
    partition_count = 8 if count >= 80 else 6
    boundaries = [round(index * count / partition_count) for index in range(partition_count + 1)]
    partitions = tuple(
        tuple(range(boundaries[index], boundaries[index + 1]))
        for index in range(partition_count)
    )
    selections = list(combinations(range(partition_count), partition_count // 2))
    failures = 0
    valid = 0
    all_partitions = set(range(partition_count))
    for selection in selections[:MAX_COMBINATIONS]:
        training_indices = tuple(
            position for partition in selection for position in partitions[partition]
        )
        testing_indices = tuple(
            position
            for partition in sorted(all_partitions.difference(selection))
            for position in partitions[partition]
        )
        training_scores = [
            _sharpe([values[index] for index in training_indices])
            for values in trials
        ]
        selected_index = max(
            range(len(trials)), key=lambda index: (training_scores[index], -index)
        )
        testing_scores = [
            _sharpe([values[index] for index in testing_indices])
            for values in trials
        ]
        ordered = sorted(range(len(trials)), key=lambda index: (testing_scores[index], index))
        rank = ordered.index(selected_index) + 1
        percentile = rank / (len(trials) + 1.0)
        failures += percentile <= 0.5
        valid += 1
    return (failures / valid if valid else None), valid


def _verdict(deflated_probability: float, pbo: float | None) -> str:
    if deflated_probability >= 0.95 and (pbo is None or pbo <= 0.20):
        return "PASS"
    if deflated_probability >= 0.80 and (pbo is None or pbo <= 0.50):
        return "CAUTION"
    return "HIGH_RISK"


def _reason(verdict: str, pbo: float | None) -> str:
    if verdict == "PASS":
        return "Sharpe 在试验次数和非正态修正后仍有统计优势"
    if verdict == "CAUTION":
        return "选择偏差修正后仅形成条件性证据"
    if pbo is not None and pbo > 0.50:
        return "组合切分显示较高回测过拟合概率"
    return "Deflated Sharpe 未通过专业审计门槛"


def _insufficient(
    trial_count: int,
    observation_count: int,
    reason: str,
) -> SelectionBiasAuditResult:
    return SelectionBiasAuditResult(
        status="INSUFFICIENT_DATA",
        verdict="NOT_EVALUATED",
        trial_count=trial_count,
        return_observation_count=observation_count,
        observed_sharpe=None,
        probabilistic_sharpe_probability=None,
        deflated_sharpe_probability=None,
        expected_maximum_sharpe=None,
        probability_of_backtest_overfitting=None,
        minimum_track_record_length=None,
        skewness=None,
        excess_kurtosis=None,
        combination_count=0,
        method="DEFLATED_SHARPE_CSCV_V1",
        reason=reason,
    )
