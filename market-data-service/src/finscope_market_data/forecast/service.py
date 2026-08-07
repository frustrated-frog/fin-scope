from __future__ import annotations

from collections.abc import Sequence
import hashlib
import math

from finscope_market_data.forecast.features import build_samples, current_features
from finscope_market_data.forecast.logistic import RegularizedLogisticModel
from finscope_market_data.forecast.schemas import (
    ForecastObservation,
    ForecastValidation,
    SingleStockForecastResult,
)
from finscope_market_data.forecast.walk_forward import (
    WalkForwardObservation,
    WalkForwardResult,
    validate_walk_forward,
)
from finscope_market_data.models import DailyBar


def build_forecast(
    bars: Sequence[DailyBar],
    *,
    instrument_code: str,
    source_code: str,
    source_family: str,
    quality_status: str,
    warnings: list[str],
) -> SingleStockForecastResult:
    ordered = sorted(bars, key=lambda item: item.trade_date)
    samples = build_samples(ordered, transaction_cost_rate=0.0015)
    base = dict(
        instrument_code=instrument_code,
        as_of_date=ordered[-1].trade_date,
        horizon_days=20,
        bar_count=len(ordered),
        data_fingerprint=_fingerprint(ordered, instrument_code),
        source_code=source_code,
        source_family=source_family,
        quality_status=quality_status,
    )
    if len(ordered) < 750:
        return SingleStockForecastResult(
            **base,
            status="INSUFFICIENT_DATA",
            conclusion="历史日线不足 750 根，当前只能观察指标，不能生成正式概率预测。",
            warnings=[*warnings, "需要更长历史覆盖才能进行滚动样本外验证"],
        )

    validation = validate_walk_forward(samples)
    model = RegularizedLogisticModel.fit(samples)
    probability = model.predict(current_features(ordered))
    comparable = [
        item
        for item in validation.observations
        if abs(item.probability - probability) <= 0.10
    ]
    if len(comparable) < 10:
        comparable = list(validation.observations)
    lower, expected, upper = _distribution(comparable)
    status, conclusion = _classify(validation, len(ordered))
    return SingleStockForecastResult(
        **base,
        status=status,
        conclusion=conclusion,
        labeled_sample_count=len(samples),
        up_probability=probability,
        expected_net_return=expected,
        lower_net_return=lower,
        upper_net_return=upper,
        validation=_validation(validation),
        recent_observations=_recent(validation.observations),
        warnings=[
            *warnings,
            "收益基于前复权日线和固定交易成本模拟，不代表真实成交回放",
        ],
    )


def _classify(validation: WalkForwardResult, bar_count: int) -> tuple[str, str]:
    edge = (
        validation.independent_sample_count >= 12
        and validation.brier_score + 0.005 < validation.baseline_brier_score
    )
    if bar_count < 1500:
        return "LOW_CONFIDENCE", "模型已完成滚动验证，但历史覆盖不足六年，只能作为低置信度观察。"
    if not edge:
        return "NO_OBSERVED_EDGE", "样本外概率尚未稳定优于该股票自身的历史上涨率，不支持据此单独交易。"
    if validation.accuracy >= 0.55 and validation.independent_sample_count >= 25:
        return "EVIDENCE_SUPPORTED", "样本外预测相对基础上涨率显示稳定增量，但仍需结合风险边界执行。"
    return "CONDITIONAL_EDGE", "样本外概率有一定增量，证据仍具条件性，应降低仓位并继续观察。"


def _validation(result: WalkForwardResult) -> ForecastValidation:
    observed_up_rate = (
        sum(item.actual_positive for item in result.observations) / len(result.observations)
        if result.observations
        else 0.0
    )
    return ForecastValidation(
        out_of_sample_count=len(result.observations),
        independent_sample_count=result.independent_sample_count,
        accuracy=result.accuracy,
        brier_score=result.brier_score,
        baseline_brier_score=result.baseline_brier_score,
        observed_up_rate=observed_up_rate,
    )


def _recent(observations: tuple[WalkForwardObservation, ...]) -> list[ForecastObservation]:
    return [
        ForecastObservation(
            signal_date=item.signal_date,
            probability=item.probability,
            actual_net_return=item.actual_return,
            correct=item.correct,
        )
        for item in reversed(observations[-12:])
    ]


def _distribution(observations: list[WalkForwardObservation]) -> tuple[float, float, float]:
    values = sorted(item.actual_return for item in observations)
    if not values:
        return 0.0, 0.0, 0.0
    lower = values[math.floor((len(values) - 1) * 0.20)]
    upper = values[math.floor((len(values) - 1) * 0.80)]
    return lower, sum(values) / len(values), upper


def _fingerprint(bars: Sequence[DailyBar], instrument_code: str) -> str:
    digest = hashlib.sha256()
    for bar in bars:
        row = "|".join(
            (
                bar.trade_date,
                instrument_code,
                str(bar.open),
                str(bar.high),
                str(bar.low),
                str(bar.close),
                str(bar.volume),
                str(bar.amount),
                bar.adjustment,
            )
        )
        digest.update((row + "\n").encode())
    return digest.hexdigest()
