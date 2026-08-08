from __future__ import annotations

from collections.abc import Sequence
from dataclasses import asdict
import hashlib
import math

from finscope_market_data.forecast.factor_catalog import FACTORS
from finscope_market_data.forecast.features import build_samples, current_features
from finscope_market_data.forecast.logistic import RegularizedLogisticModel
from finscope_market_data.forecast.performance import (
    BacktestReport,
    annual_performance,
    regime_performance,
    simulate_strategy,
)
from finscope_market_data.forecast.schemas import (
    AnnualPerformance,
    EquityPoint,
    EvaluationSlice,
    FactorExplanation,
    ForecastObservation,
    ForecastValidation,
    ParameterStability,
    PerformanceReport,
    RegimePerformance,
    SingleStockForecastResult,
    StrategyPolicy,
)
from finscope_market_data.forecast.stability import StabilityReport, analyze_stability
from finscope_market_data.forecast.walk_forward import (
    WalkForwardObservation,
    WalkForwardResult,
    validate_walk_forward,
)
from finscope_market_data.models import DailyBar


COST_RATE = 0.0015
PRIMARY_THRESHOLD = 0.60
PRIMARY_HORIZON = 20


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
    samples = build_samples(
        ordered,
        transaction_cost_rate=COST_RATE,
        horizon_days=PRIMARY_HORIZON,
    )
    policy = StrategyPolicy(
        signal_threshold=PRIMARY_THRESHOLD,
        holding_days=PRIMARY_HORIZON,
        entry_rule="T 日收盘产生信号，T+1 开盘买入",
        exit_rule="持有至第 20 个交易日收盘卖出",
        overlap_policy="持仓期间忽略新信号，不加仓、不重叠",
        round_trip_cost_rate=COST_RATE,
        benchmark="同股买入并持有",
    )
    base = dict(
        instrument_code=instrument_code,
        as_of_date=ordered[-1].trade_date,
        horizon_days=PRIMARY_HORIZON,
        bar_count=len(ordered),
        data_fingerprint=_fingerprint(ordered, instrument_code),
        source_code=source_code,
        source_family=source_family,
        quality_status=quality_status,
        last_close=ordered[-1].close,
        strategy_policy=policy,
    )
    if len(ordered) < 750:
        return SingleStockForecastResult(
            **base,
            status="INSUFFICIENT_DATA",
            conclusion="历史日线不足 750 根，无法形成可信的样本外和稳健性结论。",
            labeled_sample_count=len(samples),
            warnings=[*warnings, "需要更长历史覆盖才能进行滚动样本外验证"],
        )

    validation = validate_walk_forward(samples)
    model = RegularizedLogisticModel.fit(samples)
    features = current_features(ordered)
    probability = model.predict(features)
    comparable = [
        item
        for item in validation.observations
        if abs(item.probability - probability) <= 0.10
    ]
    if len(comparable) < 10:
        comparable = list(validation.observations)
    lower, expected, upper = _distribution(comparable)
    performance = simulate_strategy(
        ordered,
        samples,
        validation.observations,
        threshold=PRIMARY_THRESHOLD,
        holding_days=PRIMARY_HORIZON,
        round_trip_cost=COST_RATE,
    )
    stability = analyze_stability(ordered, COST_RATE)
    status, conclusion = _classify(validation, performance, stability)
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
        factor_explanations=_factor_explanations(samples, features, model),
        performance=_performance_report(performance),
        equity_curve=[EquityPoint.model_validate(asdict(item)) for item in performance.equity_curve],
        annual_performance=[
            AnnualPerformance.model_validate(asdict(item))
            for item in annual_performance(performance)
        ],
        regime_performance=[
            RegimePerformance.model_validate(asdict(item))
            for item in regime_performance(ordered, performance)
        ],
        in_sample=EvaluationSlice(
            sample_count=validation.in_sample_count,
            accuracy=validation.in_sample_accuracy,
            brier_score=validation.in_sample_brier_score,
            evidence_role="拟合诊断，不作为有效性证据",
        ),
        out_of_sample=EvaluationSlice(
            sample_count=len(validation.observations),
            accuracy=validation.accuracy,
            brier_score=validation.brier_score,
            baseline_brier_score=validation.baseline_brier_score,
            evidence_role="扩展窗口滚动验证，决定最终结论",
        ),
        parameter_stability=ParameterStability.model_validate(asdict(stability)),
        recent_observations=_recent(validation.observations),
        warnings=[
            *warnings,
            "收益基于前复权日线、固定规则和固定交易成本模拟，不代表真实成交回放",
            "因子贡献解释模型输出，不证明因果关系",
        ],
    )


def _classify(
    validation: WalkForwardResult,
    performance: BacktestReport,
    stability: StabilityReport,
) -> tuple[str, str]:
    probability_edge = (
        validation.independent_sample_count >= 12
        and validation.brier_score + 0.005 < validation.baseline_brier_score
    )
    enough_trades = performance.trade_count >= 8
    robust = (
        validation.independent_sample_count >= 25
        and enough_trades
        and probability_edge
        and performance.excess_return > 0
        and performance.strategy.sharpe_ratio > performance.benchmark.sharpe_ratio
        and stability.positive_excess_ratio >= 0.80
        and stability.worst_excess_return > -0.02
    )
    if robust:
        return "ROBUST", "样本外概率、同股超额收益和相邻参数方向一致，当前证据支持稳健但非确定性的优势。"
    conditional = (
        validation.independent_sample_count >= 12
        and performance.trade_count >= 3
        and (probability_edge or performance.excess_return > 0)
        and stability.positive_excess_ratio >= 0.60
    )
    if conditional:
        return "CONDITIONAL", "优势只在部分指标、阶段或相邻参数下成立，应按条件性证据继续观察。"
    return "NO_CLEAR_EDGE", "样本外结果没有稳定优于同股买入并持有，不支持把当前概率单独作为交易依据。"


def _factor_explanations(samples, features, model) -> list[FactorExplanation]:
    normalized = model.normalized(features)
    contributions = model.contributions(features)
    result: list[FactorExplanation] = []
    for index, definition in enumerate(FACTORS):
        history = [item.features[index] for item in samples]
        percentile = sum(item <= features[index] for item in history) / len(history)
        contribution = contributions[index]
        result.append(
            FactorExplanation(
                code=definition.code,
                name=definition.name,
                category=definition.category,
                formula=definition.formula,
                window=definition.window,
                current_value=features[index],
                historical_percentile=percentile,
                standardized_value=normalized[index],
                coefficient=float(model.weights[index + 1]),
                contribution=contribution,
                direction=(
                    "支持上涨" if contribution > 1e-9 else "压低概率" if contribution < -1e-9 else "影响中性"
                ),
                economic_meaning=definition.economic_meaning,
                boundary=definition.boundary,
            )
        )
    return result


def _performance_report(report: BacktestReport) -> PerformanceReport:
    payload = asdict(report)
    payload.pop("equity_curve")
    return PerformanceReport.model_validate(payload)


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
    digest.update(b"single-stock-research-v2|logistic-walk-forward-v2|20|0.60|0.0015\n")
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
