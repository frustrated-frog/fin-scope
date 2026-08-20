from __future__ import annotations

from collections import Counter, defaultdict
from datetime import date, timedelta
import math
import statistics
from typing import Iterable, Sequence

from finscope_market_data.discovery.schemas import (
    DiscoveryEvaluationReport,
    DiscoveryEvaluationRequest,
    DiscoveryModelMetric,
    DiscoveryModelObservation,
    DiscoveryModelRace,
    DiscoveryOutcomeObservation,
    DiscoveryProbabilityQuality,
    DiscoveryRecentOutcome,
    DiscoveryReliabilityBin,
    DiscoverySectorPerformance,
    DiscoverySelectionMetric,
    DiscoveryWindowMetric,
)


MINIMUM_HEALTH_SAMPLES = 30
MINIMUM_HEALTH_RUNS = 5
MINIMUM_PROMOTION_SAMPLES = 30


def evaluate_discovery_outcomes(
    request: DiscoveryEvaluationRequest,
) -> DiscoveryEvaluationReport:
    _assert_unique_outcomes(request.observations)
    _assert_unique_models(request.model_observations)
    ordered = sorted(
        request.observations,
        key=lambda item: (item.as_of_date, item.run_id, item.instrument_code),
    )
    as_of = _as_of_date(request, ordered)
    finals = [item for item in ordered if item.final_rank is not None]
    quality = _probability_quality(ordered)
    matured_runs = len({item.run_id for item in ordered})
    status, conclusion = _health(quality, matured_runs, finals)
    return DiscoveryEvaluationReport(
        as_of_date=as_of.isoformat(),
        status=status,
        conclusion=conclusion,
        matured_run_count=matured_runs,
        matured_candidate_count=len(ordered),
        matured_final_count=len(finals),
        pending_count=request.pending_count,
        probability_quality=quality,
        reliability_bins=_reliability_bins(ordered),
        selection_metrics=[_selection_metric(ordered, limit) for limit in (1, 3, 5)],
        windows=[_window_metric(ordered, as_of, days) for days in (30, 90, 180)],
        sector_performance=_sector_performance(finals),
        model_race=_model_race(request.model_observations),
        recent_outcomes=_recent_outcomes(finals),
        warnings=_warnings(quality, matured_runs, finals),
    )


def _assert_unique_outcomes(values: Sequence[DiscoveryOutcomeObservation]) -> None:
    keys = [(item.run_id, item.instrument_code) for item in values]
    if len(keys) != len(set(keys)):
        raise ValueError("股票发现评测包含重复候选观察")


def _assert_unique_models(values: Sequence[DiscoveryModelObservation]) -> None:
    keys = [(item.run_id, item.instrument_code, item.model_code) for item in values]
    if len(keys) != len(set(keys)):
        raise ValueError("股票发现评测包含重复模型观察")


def _as_of_date(
    request: DiscoveryEvaluationRequest,
    values: Sequence[DiscoveryOutcomeObservation],
) -> date:
    if request.as_of_date is not None:
        return date.fromisoformat(request.as_of_date)
    if values:
        return max(date.fromisoformat(item.as_of_date) for item in values)
    return date.today()


def _probability_quality(
    values: Iterable[DiscoveryOutcomeObservation],
) -> DiscoveryProbabilityQuality:
    samples = [item for item in values if item.calibrated_probability is not None]
    if not samples:
        return DiscoveryProbabilityQuality()
    probabilities = [float(item.calibrated_probability) for item in samples]
    labels = [1.0 if item.actual_direction == "UP" else 0.0 for item in samples]
    baseline = statistics.fmean(labels)
    brier = statistics.fmean(
        (probability - label) ** 2
        for probability, label in zip(probabilities, labels)
    )
    baseline_brier = statistics.fmean((baseline - label) ** 2 for label in labels)
    bounded = [_bounded(item) for item in probabilities]
    log_loss = statistics.fmean(
        -(label * math.log(probability) + (1 - label) * math.log(1 - probability))
        for probability, label in zip(bounded, labels)
    )
    accuracy = statistics.fmean(
        (probability >= 0.5) == bool(label)
        for probability, label in zip(probabilities, labels)
    )
    bins = _reliability_bins(samples)
    ece = sum(
        item.count * float(item.calibration_error or 0.0) for item in bins
    ) / len(samples)
    return DiscoveryProbabilityQuality(
        sample_count=len(samples),
        brier_score=brier,
        brier_skill_score=(
            1.0 - brier / baseline_brier if baseline_brier > 1e-12 else 0.0
        ),
        log_loss=log_loss,
        accuracy=accuracy,
        expected_calibration_error=ece,
        baseline_probability=baseline,
    )


def _reliability_bins(
    values: Iterable[DiscoveryOutcomeObservation],
) -> list[DiscoveryReliabilityBin]:
    samples = [item for item in values if item.calibrated_probability is not None]
    result: list[DiscoveryReliabilityBin] = []
    for index in range(5):
        lower, upper = index / 5.0, (index + 1) / 5.0
        selected = [
            item for item in samples
            if float(item.calibrated_probability) >= lower
            and (
                float(item.calibrated_probability) < upper
                or index == 4 and float(item.calibrated_probability) <= upper
            )
        ]
        mean_probability = (
            statistics.fmean(float(item.calibrated_probability) for item in selected)
            if selected else None
        )
        observed = (
            statistics.fmean(item.actual_direction == "UP" for item in selected)
            if selected else None
        )
        result.append(DiscoveryReliabilityBin(
            lower_bound=lower,
            upper_bound=upper,
            count=len(selected),
            mean_probability=mean_probability,
            observed_up_rate=observed,
            calibration_error=(
                abs(mean_probability - observed)
                if mean_probability is not None and observed is not None else None
            ),
        ))
    return result


def _selection_metric(
    values: Sequence[DiscoveryOutcomeObservation], limit: int
) -> DiscoverySelectionMetric:
    by_run: dict[int, list[DiscoveryOutcomeObservation]] = defaultdict(list)
    for item in values:
        by_run[item.run_id].append(item)
    selected: list[DiscoveryOutcomeObservation] = []
    pool_returns: list[float] = []
    excess_values: list[float] = []
    run_count = 0
    for run_values in by_run.values():
        current = [
            item for item in run_values
            if item.final_rank is not None and item.final_rank <= limit
        ]
        if not current:
            continue
        run_count += 1
        selected.extend(current)
        pool = [item.actual_net_return for item in run_values if item.admitted]
        pool_average = statistics.fmean(pool)
        pool_returns.extend(pool)
        excess_values.append(
            statistics.fmean(item.actual_net_return for item in current) - pool_average
        )
    returns = [item.actual_net_return for item in selected]
    return DiscoverySelectionMetric(
        limit=limit,
        matured_run_count=run_count,
        sample_count=len(selected),
        hit_rate=(
            statistics.fmean(item.actual_direction == "UP" for item in selected)
            if selected else None
        ),
        average_net_return=statistics.fmean(returns) if returns else None,
        median_net_return=statistics.median(returns) if returns else None,
        admitted_pool_average_return=(
            statistics.fmean(pool_returns) if pool_returns else None
        ),
        average_excess_vs_admitted_pool=(
            statistics.fmean(excess_values) if excess_values else None
        ),
    )


def _window_metric(
    values: Sequence[DiscoveryOutcomeObservation], as_of: date, days: int
) -> DiscoveryWindowMetric:
    start = as_of - timedelta(days=days - 1)
    selected = [item for item in values if date.fromisoformat(item.as_of_date) >= start]
    finals = [item for item in selected if item.final_rank is not None]
    quality = _probability_quality(selected)
    return DiscoveryWindowMetric(
        window_days=days,
        start_date=start.isoformat(),
        matured_run_count=len({item.run_id for item in selected}),
        probability_sample_count=quality.sample_count,
        final_count=len(finals),
        final_hit_rate=(
            statistics.fmean(item.actual_direction == "UP" for item in finals)
            if finals else None
        ),
        final_average_net_return=(
            statistics.fmean(item.actual_net_return for item in finals)
            if finals else None
        ),
        brier_skill_score=quality.brier_skill_score,
    )


def _sector_performance(
    values: Sequence[DiscoveryOutcomeObservation],
) -> list[DiscoverySectorPerformance]:
    grouped: dict[str, list[DiscoveryOutcomeObservation]] = defaultdict(list)
    for item in values:
        for sector in set(item.sector_names):
            if sector.strip():
                grouped[sector.strip()].append(item)
    result = [
        DiscoverySectorPerformance(
            sector_name=sector,
            sample_count=len(items),
            hit_rate=statistics.fmean(item.actual_direction == "UP" for item in items),
            average_net_return=statistics.fmean(item.actual_net_return for item in items),
        )
        for sector, items in grouped.items()
    ]
    return sorted(
        result,
        key=lambda item: (-item.sample_count, -item.average_net_return, item.sector_name),
    )[:12]


def _model_race(values: Sequence[DiscoveryModelObservation]) -> DiscoveryModelRace:
    if not values:
        return DiscoveryModelRace(
            conclusion="尚无股票发现模型的真实到期成对结果。"
        )
    grouped: dict[str, list[DiscoveryModelObservation]] = defaultdict(list)
    for item in values:
        grouped[item.model_code].append(item)
    champion_codes = [
        code for code, items in grouped.items()
        if Counter(item.role for item in items).most_common(1)[0][0] == "CHAMPION"
    ]
    if len(champion_codes) != 1:
        return DiscoveryModelRace(
            status="EVIDENCE_INCOMPLETE",
            conclusion="真实模型赛马缺少唯一冠军的成对证据。",
        )
    champion_code = champion_codes[0]
    raw_metrics = {
        code: _model_metric(items, champion_code)
        for code, items in grouped.items()
    }
    champion = raw_metrics[champion_code]
    metrics: list[DiscoveryModelMetric] = []
    for code, metric in raw_metrics.items():
        accuracy = metric.covered_accuracy or 0.0
        champion_accuracy = champion.covered_accuracy or 0.0
        eligible = (
            metric.role == "CHALLENGER"
            and metric.sample_count >= MINIMUM_PROMOTION_SAMPLES
            and metric.brier_score - champion.brier_score <= -0.01
            and metric.log_loss - champion.log_loss <= 0.0
            and metric.coverage >= 0.40
            and accuracy >= champion_accuracy
        )
        metrics.append(metric.model_copy(update={
            "brier_delta_vs_champion": metric.brier_score - champion.brier_score,
            "log_loss_delta_vs_champion": metric.log_loss - champion.log_loss,
            "promotion_eligible": eligible,
        }))
    metrics.sort(key=lambda item: (item.brier_score, item.model_code))
    eligible = next((item for item in metrics if item.promotion_eligible), None)
    champion_samples = champion.sample_count
    if champion_samples < MINIMUM_PROMOTION_SAMPLES:
        status = "EVIDENCE_ACCUMULATING"
        conclusion = "真实成对模型样本未达到 30 次，继续影子运行。"
    elif eligible is not None:
        status = "PROMOTION_REVIEW"
        conclusion = "挑战者同时通过真实概率质量、覆盖率和命中门槛，进入人工晋升复核。"
    elif metrics[0].model_code == champion_code:
        status = "CHAMPION_LEADS"
        conclusion = "当前冠军在真实到期概率质量上保持领先。"
    else:
        status = "NO_STABLE_EDGE"
        conclusion = "存在局部改善，但没有挑战者同时越过全部晋升门槛。"
    return DiscoveryModelRace(
        status=status,
        sample_count=champion_samples,
        champion_code=champion_code,
        promotion_candidate_code=eligible.model_code if eligible else None,
        conclusion=conclusion,
        candidates=metrics,
    )


def _model_metric(
    values: Sequence[DiscoveryModelObservation], champion_code: str
) -> DiscoveryModelMetric:
    probabilities = [_bounded(item.calibrated_probability) for item in values]
    labels = [1.0 if item.actual_direction == "UP" else 0.0 for item in values]
    covered = [item for item in values if item.shadow_decision != "ABSTAIN"]
    role = Counter(item.role for item in values).most_common(1)[0][0]
    return DiscoveryModelMetric(
        model_code=values[0].model_code,
        model_name=values[0].model_name,
        role=role,
        sample_count=len(values),
        brier_score=statistics.fmean(
            (probability - label) ** 2
            for probability, label in zip(probabilities, labels)
        ),
        log_loss=statistics.fmean(
            -(label * math.log(probability) + (1 - label) * math.log(1 - probability))
            for probability, label in zip(probabilities, labels)
        ),
        covered_count=len(covered),
        coverage=len(covered) / len(values),
        covered_accuracy=(
            statistics.fmean(item.shadow_decision == item.actual_direction for item in covered)
            if covered else None
        ),
        brier_delta_vs_champion=0.0 if values[0].model_code == champion_code else 1.0,
        log_loss_delta_vs_champion=0.0 if values[0].model_code == champion_code else 1.0,
        promotion_eligible=False,
    )


def _recent_outcomes(
    values: Sequence[DiscoveryOutcomeObservation],
) -> list[DiscoveryRecentOutcome]:
    ordered = sorted(
        values,
        key=lambda item: (item.as_of_date, item.run_id, -(item.final_rank or 99)),
        reverse=True,
    )
    return [
        DiscoveryRecentOutcome(
            run_id=item.run_id,
            instrument_code=item.instrument_code,
            as_of_date=item.as_of_date,
            final_rank=item.final_rank or 1,
            calibrated_probability=item.calibrated_probability,
            actual_net_return=item.actual_net_return,
            actual_direction=item.actual_direction,
            sector_names=item.sector_names,
        )
        for item in ordered[:20]
    ]


def _health(
    quality: DiscoveryProbabilityQuality,
    matured_runs: int,
    finals: Sequence[DiscoveryOutcomeObservation],
) -> tuple[str, str]:
    if quality.sample_count < MINIMUM_HEALTH_SAMPLES or matured_runs < MINIMUM_HEALTH_RUNS:
        return "ACCUMULATING", "真实到期样本仍在积累，当前不对预测能力下结论。"
    average_return = (
        statistics.fmean(item.actual_net_return for item in finals) if finals else 0.0
    )
    if (quality.brier_skill_score or 0.0) > 0.0 and average_return >= 0.0:
        return "HEALTHY", "真实概率质量优于窗口基准，最终候选平均净收益未转负。"
    return "WATCH", "真实概率或最终候选收益尚未形成稳定优势，维持观察而不提高置信度。"


def _warnings(
    quality: DiscoveryProbabilityQuality,
    matured_runs: int,
    finals: Sequence[DiscoveryOutcomeObservation],
) -> list[str]:
    warnings: list[str] = []
    if quality.sample_count < MINIMUM_HEALTH_SAMPLES:
        warnings.append(f"概率样本 {quality.sample_count}/30，统计结论尚不稳定")
    if matured_runs < MINIMUM_HEALTH_RUNS:
        warnings.append(f"到期批次 {matured_runs}/5，需覆盖更多独立交易日")
    if not finals:
        warnings.append("尚无最终候选到期结果")
    return warnings


def _bounded(value: float) -> float:
    return max(0.000001, min(0.999999, float(value)))
