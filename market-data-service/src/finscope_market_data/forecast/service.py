from __future__ import annotations

from collections.abc import Sequence
from dataclasses import asdict
import hashlib
import math
import statistics

from finscope_market_data.forecast.bootstrap import (
    ConfidenceInterval as BootstrapConfidenceInterval,
    bootstrap_interval,
    paired_annualized_excess,
)
from finscope_market_data.forecast.calibration import PlattCalibrator
from finscope_market_data.forecast.factor_catalog import FACTORS
from finscope_market_data.forecast.context import AlignedForecastContext
from finscope_market_data.forecast.features import FEATURE_CODES, build_samples, current_features
from finscope_market_data.forecast.model_competition import run_model_competition
from finscope_market_data.forecast.performance import (
    BacktestReport,
    annual_performance,
    regime_performance,
    simulate_strategy,
)
from finscope_market_data.forecast.schemas import (
    AnnualPerformance,
    CalibrationReport,
    ConfidenceInterval,
    EquityPoint,
    EvaluationSlice,
    FactorExplanation,
    ForecastObservation,
    ForecastValidation,
    ForecastContextReport,
    ContextSource,
    LeakageAudit,
    LockedTestReport,
    ModelCandidate,
    ModelCompetitionReport,
    ModelQualification as ModelQualificationSchema,
    ParameterStability,
    PerformanceReport,
    ProbabilityMetricSet,
    QualificationIntervals,
    QualificationSplitAudit,
    RegimePerformance,
    ReliabilityBin,
    SelectiveValidation,
    SingleStockForecastResult,
    SplitSliceAudit,
    StrategyPolicy,
    TrialIdentity,
)
from finscope_market_data.forecast.qualification import (
    ModelQualification,
    evaluate_probability_metrics,
    qualify_model,
    selective_metrics,
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
DEFAULT_HORIZON = 5
MODEL_VERSION = "competition-platt-selective-v5.1"
REPORT_VERSION = "single-stock-research-v5"


def build_forecast(
    bars: Sequence[DailyBar],
    *,
    instrument_code: str,
    source_code: str,
    source_family: str,
    quality_status: str,
    warnings: list[str],
    horizon_days: int = DEFAULT_HORIZON,
    context: AlignedForecastContext | None = None,
) -> SingleStockForecastResult:
    if horizon_days not in (1, 5, 20):
        raise ValueError("单股预测只支持 1、5、20 日周期")
    ordered = sorted(bars, key=lambda item: item.trade_date)
    samples = build_samples(
        ordered,
        transaction_cost_rate=COST_RATE,
        horizon_days=horizon_days,
        context=context,
    )
    policy = StrategyPolicy(
        signal_threshold=PRIMARY_THRESHOLD,
        holding_days=horizon_days,
        entry_rule="T 日收盘产生信号，T+1 开盘买入",
        exit_rule=f"持有 {horizon_days} 个完整交易日，T+{horizon_days + 1} 开盘卖出",
        overlap_policy="持仓期间忽略新信号，不加仓、不重叠",
        round_trip_cost_rate=COST_RATE,
        benchmark="同股买入并持有",
    )
    data_fingerprint = _fingerprint(
        ordered, instrument_code, horizon_days, context=context
    )
    base = dict(
        instrument_code=instrument_code,
        as_of_date=ordered[-1].trade_date,
        horizon_days=horizon_days,
        bar_count=len(ordered),
        data_fingerprint=data_fingerprint,
        source_code=source_code,
        source_family=source_family,
        quality_status=quality_status,
        last_close=ordered[-1].close,
        strategy_policy=policy,
        context=_context_report(context),
        leakage_audit=_leakage_audit(samples, len(FEATURE_CODES)),
    )
    if len(ordered) < 750:
        return SingleStockForecastResult(
            **base,
            status="INSUFFICIENT_DATA",
            conclusion="历史日线不足 750 根，无法形成可信的样本外和稳健性结论。",
            decision="ABSTAIN",
            decision_reason="历史数据不足，拒绝输出方向判断。",
            labeled_sample_count=len(samples),
            warnings=[*warnings, "需要更长历史覆盖才能进行滚动样本外验证"],
        )

    competition = run_model_competition(samples, independent_stride_days=horizon_days)
    selected_model = competition.selected_model
    validation = validate_walk_forward(
        samples, independent_stride_days=horizon_days, model_code=selected_model
    )
    qualification = qualify_model(
        samples, independent_stride_days=horizon_days, model_code=selected_model
    )
    features = current_features(ordered, context=context)
    raw_probability = qualification.model.predict(features)
    probability = qualification.calibration.calibrate(raw_probability)
    comparable = _comparable_observations(validation.observations, raw_probability)
    lower, expected, upper = _distribution(comparable)
    performance = simulate_strategy(
        ordered,
        samples,
        validation.observations,
        threshold=PRIMARY_THRESHOLD,
        holding_days=horizon_days,
        round_trip_cost=COST_RATE,
    )
    stability = analyze_stability(
        ordered,
        COST_RATE,
        horizon_days=horizon_days,
        model_code=selected_model,
        context=context,
    )
    seed = _seed(data_fingerprint, "qualification")
    intervals = _qualification_intervals(qualification, performance, seed)
    probability_interval = _probability_interval(
        qualification,
        raw_probability,
        _seed(data_fingerprint, "current-probability"),
    ) if qualification.status != "INSUFFICIENT_DATA" else _unavailable_interval(
        "校准区或锁定测试区独立锚点不足"
    )
    status, conclusion = _classify(validation, performance, stability, qualification, intervals)
    decision, decision_reason = _decision(probability, qualification.status)
    selective = selective_metrics(
        qualification.locked_test.calibrated_probabilities,
        qualification.locked_test.labels,
        lower_threshold=1.0 - PRIMARY_THRESHOLD,
        upper_threshold=PRIMARY_THRESHOLD,
    )
    runtime_model_version = f"competition-{selected_model.lower()}-platt-v5.1"
    trial = _trial(data_fingerprint, seed, horizon_days, runtime_model_version)
    return SingleStockForecastResult(
        **base,
        status=status,
        model_version=runtime_model_version,
        conclusion=conclusion,
        decision=decision,
        decision_reason=decision_reason,
        labeled_sample_count=len(samples),
        up_probability=None if status == "INSUFFICIENT_DATA" else probability,
        raw_probability=None if status == "INSUFFICIENT_DATA" else raw_probability,
        probability_interval=_interval(
            probability_interval,
            limitation="仅覆盖校准映射的抽样误差，不覆盖模型、突发事件与市场结构变化",
        ),
        expected_net_return=expected,
        lower_net_return=lower,
        upper_net_return=upper,
        validation=_validation(validation),
        factor_explanations=_factor_explanations(
            samples, features, qualification.explanation_model,
            selected_model=selected_model,
        ),
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
        qualification=_qualification_report(
            qualification,
            trial,
            intervals,
            horizon_days,
        ),
        selective_validation=SelectiveValidation.model_validate(asdict(selective)),
        model_competition=ModelCompetitionReport(
            selected_model=competition.selected_model,
            selection_end_date=competition.selection_end_date,
            calibration_start_date=competition.calibration_start_date,
            selection_rule=competition.selection_rule,
            candidates=[ModelCandidate.model_validate(asdict(item)) for item in competition.candidates],
        ),
        warnings=[
            *warnings,
            "收益基于滚动原始概率、前复权日线、固定规则和固定交易成本模拟，不代表校准后概率策略或真实成交回放",
            "因子贡献解释模型输出，不证明因果关系",
            *(
                ["冠军为非线性或规则模型；因子贡献使用同训练集逻辑回归作为可解释代理，不等同于冠军模型内部归因"]
                if selected_model != "LOGISTIC"
                else []
            ),
            "主概率经过独立校准区 Platt 校准；锁定测试从未参与模型或校准器拟合",
            "方向判断允许弃权；覆盖后命中率必须与覆盖率同时阅读",
        ],
    )


def _decision(probability: float, qualification_status: str) -> tuple[str, str]:
    if qualification_status in {"FAILED", "INSUFFICIENT_DATA"}:
        return "ABSTAIN", "模型未通过当前周期的资格门槛，拒绝输出方向。"
    if probability >= PRIMARY_THRESHOLD:
        return "UP", "校准上涨概率达到预注册上阈值。"
    if probability <= 1.0 - PRIMARY_THRESHOLD:
        return "DOWN", "校准上涨概率低于预注册下阈值。"
    return "ABSTAIN", "概率位于拒绝区间，当前信息不足以形成方向优势。"


def _classify(
    validation: WalkForwardResult,
    performance: BacktestReport,
    stability: StabilityReport,
    qualification: ModelQualification,
    intervals: QualificationIntervals,
) -> tuple[str, str]:
    if qualification.status == "INSUFFICIENT_DATA":
        return "INSUFFICIENT_DATA", "历史日线虽可建模，但校准区或锁定测试区的独立锚点/正负标签不足，拒绝输出预测概率。"
    locked = qualification.locked_test.calibrated_metrics
    brier_interval = intervals.brier_skill_score
    probability_edge = locked.brier_skill_score > 0 and (
        brier_interval.lower is None or brier_interval.lower > -0.05
    )
    enough_trades = performance.trade_count >= 8
    robust = (
        qualification.status == "QUALIFIED"
        and locked.sample_count >= 15
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
        locked.sample_count >= 15
        and performance.trade_count >= 3
        and (probability_edge or performance.excess_return > 0)
        and stability.positive_excess_ratio >= 0.60
    )
    if conditional:
        return "CONDITIONAL", "优势只在部分指标、阶段或相邻参数下成立，应按条件性证据继续观察。"
    return "NO_CLEAR_EDGE", "样本外结果没有稳定优于同股买入并持有，不支持把当前概率单独作为交易依据。"


def _qualification_intervals(
    qualification: ModelQualification,
    performance: BacktestReport,
    seed: int,
) -> QualificationIntervals:
    locked = qualification.locked_test
    enough_locked = len(locked.labels) >= 15
    brier = bootstrap_interval(
        len(locked.labels),
        lambda indices: evaluate_probability_metrics(
            [locked.calibrated_probabilities[index] for index in indices],
            [locked.labels[index] for index in indices],
            locked.baseline_probability,
        ).brier_skill_score,
        block_length=3,
        iterations=1000,
        seed=seed,
    ) if enough_locked else _unavailable_interval("锁定测试独立锚点少于 15 个")
    accuracy = bootstrap_interval(
        len(locked.labels),
        lambda indices: sum(
            (locked.calibrated_probabilities[index] >= 0.5) == locked.labels[index]
            for index in indices
        ) / len(indices),
        block_length=3,
        iterations=1000,
        seed=seed + 1,
    ) if enough_locked else _unavailable_interval("锁定测试独立锚点少于 15 个")
    strategy_returns, benchmark_returns = _daily_returns(performance)
    excess = bootstrap_interval(
        len(strategy_returns),
        lambda indices: paired_annualized_excess(
            strategy_returns,
            benchmark_returns,
            indices,
        ),
        block_length=20,
        iterations=1000,
        seed=seed + 2,
    )
    sharpe = bootstrap_interval(
        len(strategy_returns),
        lambda indices: _sharpe([strategy_returns[index] for index in indices]),
        block_length=20,
        iterations=1000,
        seed=seed + 3,
    )
    return QualificationIntervals(
        brier_skill_score=_interval(brier),
        accuracy=_interval(accuracy),
        excess_return=_interval(excess),
        sharpe_ratio=_interval(sharpe),
    )


def _probability_interval(
    qualification: ModelQualification,
    raw_probability: float,
    seed: int,
) -> BootstrapConfidenceInterval:
    raw = qualification.calibration_raw_probabilities
    labels = qualification.calibration_labels
    return bootstrap_interval(
        len(raw),
        lambda indices: PlattCalibrator.fit(
            [raw[index] for index in indices],
            [labels[index] for index in indices],
        ).calibrate(raw_probability),
        block_length=3,
        iterations=500,
        seed=seed,
    )


def _daily_returns(report: BacktestReport) -> tuple[list[float], list[float]]:
    strategy: list[float] = []
    benchmark: list[float] = []
    for previous, current in zip(report.equity_curve[:-1], report.equity_curve[1:]):
        strategy.append(current.strategy_nav / previous.strategy_nav - 1.0)
        benchmark.append(current.benchmark_nav / previous.benchmark_nav - 1.0)
    return strategy, benchmark


def _sharpe(returns: list[float]) -> float:
    if len(returns) < 2:
        raise ValueError("Sharpe 样本不足")
    deviation = statistics.stdev(returns)
    if deviation < 1e-12:
        raise ValueError("Sharpe 波动率为零")
    return statistics.mean(returns) / deviation * math.sqrt(252.0)


def _qualification_report(
    qualification: ModelQualification,
    trial: TrialIdentity,
    intervals: QualificationIntervals,
    horizon_days: int,
) -> ModelQualificationSchema:
    calibration = qualification.calibration
    locked = qualification.locked_test
    return ModelQualificationSchema(
        status=qualification.status,
        reason=qualification.reason,
        trial=trial,
        split_audit=QualificationSplitAudit(
            development=SplitSliceAudit.model_validate(asdict(qualification.split_audit.development)),
            calibration=SplitSliceAudit.model_validate(asdict(qualification.split_audit.calibration)),
            locked_test=SplitSliceAudit.model_validate(asdict(qualification.split_audit.locked_test)),
            label_horizon_days=horizon_days,
            independent_stride_days=horizon_days,
            rule="严格前向 60/20/20；训练标签退出日必须早于待预测日",
        ),
        calibration=CalibrationReport(
            status=calibration.status,
            sample_count=calibration.sample_count,
            positive_count=calibration.positive_count,
            slope=calibration.slope,
            intercept=calibration.intercept,
            raw_log_loss=calibration.raw_log_loss,
            calibrated_log_loss=calibration.calibrated_log_loss,
            reason=calibration.reason,
        ),
        locked_test=LockedTestReport(
            baseline_probability=locked.baseline_probability,
            raw_metrics=ProbabilityMetricSet.model_validate(asdict(locked.raw_metrics)),
            calibrated_metrics=ProbabilityMetricSet.model_validate(asdict(locked.calibrated_metrics)),
            baseline_metrics=ProbabilityMetricSet.model_validate(asdict(locked.baseline_metrics)),
            reliability_bins=[ReliabilityBin.model_validate(asdict(item)) for item in locked.reliability_bins],
        ),
        confidence_intervals=intervals,
    )


def _interval(
    interval: BootstrapConfidenceInterval,
    *,
    limitation: str | None = None,
) -> ConfidenceInterval:
    return ConfidenceInterval(
        status=interval.status,
        lower=interval.lower,
        upper=interval.upper,
        confidence_level=interval.confidence_level,
        method=interval.method,
        valid_iterations=interval.valid_iterations,
        reason=interval.reason,
        limitation=limitation,
    )


def _unavailable_interval(reason: str) -> BootstrapConfidenceInterval:
    return BootstrapConfidenceInterval(
        status="UNAVAILABLE",
        lower=None,
        upper=None,
        confidence_level=0.95,
        method="MOVING_BLOCK_BOOTSTRAP",
        valid_iterations=0,
        reason=reason,
    )


def _trial(
    data_fingerprint: str, seed: int, horizon_days: int, model_version: str
) -> TrialIdentity:
    identity = "|".join(
        (
            data_fingerprint,
            model_version,
            REPORT_VERSION,
            "price-volume-context-20-v1",
            f"t1-open-net-return-{horizon_days}d-v2",
            "forward-60-20-20-purged-v1",
            "platt-v1",
            "moving-block-v1",
            str(PRIMARY_THRESHOLD),
            str(COST_RATE),
        )
    )
    return TrialIdentity(
        trial_id=hashlib.sha256(identity.encode()).hexdigest(),
        feature_version="price-volume-context-20-v1",
        label_version=f"t1-open-net-return-{horizon_days}d-v2",
        split_version="forward-60-20-20-purged-v1",
        calibration_version="platt-v1",
        bootstrap_version="moving-block-v1",
        random_seed=seed,
        model_version=model_version,
    )


def _context_report(context: AlignedForecastContext | None) -> ForecastContextReport:
    market_coverage = context.market_coverage if context is not None else 0.0
    industry_coverage = context.industry_coverage if context is not None else 0.0
    return ForecastContextReport(
        market=ContextSource(
            code=context.market_code if context is not None else None,
            label="沪深300",
            status="AVAILABLE" if market_coverage >= 0.95 else "DEGRADED",
            coverage=market_coverage,
            regime=context.market_regime if context is not None else None,
            reason=None if market_coverage >= 0.95 else "市场日线未完整覆盖目标股票交易日",
        ),
        industry=ContextSource(
            code=context.industry_code if context is not None else None,
            label="行业代理指数",
            status="AVAILABLE" if industry_coverage >= 0.95 else "UNAVAILABLE",
            coverage=industry_coverage,
            regime=context.industry_regime if context is not None else None,
            reason=None if industry_coverage >= 0.95 else "未匹配到具有可靠历史覆盖的行业代理",
        ),
        feature_codes=list(item.code for item in FACTORS),
        alignment_rule="只按目标股票交易日左连接；缺失上下文使用中性值，禁止向未来填充",
    )


def _leakage_audit(samples, feature_count: int) -> LeakageAudit:
    checks = [
        "样本标签满足信号日 < T+1 入场日 <= 退出日",
        "每个样本特征维度与版本目录一致且数值有限",
        "训练到期标签、上下文对齐及模型切分由对应回归测试约束",
    ]
    valid = all(
        item.signal_date < item.entry_date <= item.exit_date
        and len(item.features) == feature_count
        and all(math.isfinite(value) for value in item.features)
        for item in samples
    )
    return LeakageAudit(
        status="PASSED" if valid else "FAILED",
        checked_sample_count=len(samples),
        checks=checks,
    )


def _seed(data_fingerprint: str, purpose: str) -> int:
    digest = hashlib.sha256(f"{data_fingerprint}|{MODEL_VERSION}|{purpose}".encode()).hexdigest()
    return int(digest[:8], 16) & 0x7FFFFFFF


def _factor_explanations(
    samples, features, model, *, selected_model: str
) -> list[FactorExplanation]:
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
                boundary=(
                    definition.boundary
                    if selected_model == "LOGISTIC"
                    else f"{definition.boundary}；当前贡献为逻辑回归代理解释，不是 {selected_model} 的内部归因。"
                ),
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


def _comparable_observations(
    observations: Sequence[WalkForwardObservation], raw_probability: float
) -> list[WalkForwardObservation]:
    comparable = [
        item
        for item in observations
        if abs(item.probability - raw_probability) <= 0.10
    ]
    return comparable if len(comparable) >= 10 else list(observations)


def _fingerprint(
    bars: Sequence[DailyBar],
    instrument_code: str,
    horizon_days: int,
    *,
    context: AlignedForecastContext | None,
) -> str:
    digest = hashlib.sha256()
    digest.update(
        f"{REPORT_VERSION}|{MODEL_VERSION}|{horizon_days}|0.60|0.0015\n".encode()
    )
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
    if context is not None:
        for role, aligned in (
            ("MARKET", context.market_bars),
            ("INDUSTRY", context.industry_bars),
        ):
            for target, bar in zip(bars, aligned):
                if bar is None:
                    digest.update(f"{role}|{target.trade_date}|MISSING\n".encode())
                    continue
                row = "|".join(
                    (
                        role,
                        target.trade_date,
                        bar.symbol.cache_key,
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
