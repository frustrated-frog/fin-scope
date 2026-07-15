package com.finscope.service.marketintel;

import com.finscope.dao.marketintel.CapitalBehaviorEvaluationRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.domain.marketintel.MarketIntelRefreshStep;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.service.marketdata.CapitalFlowGatewayResult;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.QuoteGatewayResult;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** 资金行为刷新编排；数据源选择、切换和熔断统一由 MarketDataGateway 负责。 */
@Service
public class MarketIntelRefreshCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MarketIntelRefreshCoordinator.class);
    private final MarketIntelCapitalService capital;
    private final MarketDataGateway gateway;
    private final CapitalFlowRepository flows;
    private final CapitalBehaviorSignalService signalService;
    private final CapitalBehaviorSnapshotFactory snapshotsFactory;
    private final CapitalBehaviorSnapshotRepository snapshots;
    private final CapitalRuleExplanationService ruleService;
    private final CapitalInterpretationRepository interpretations;
    private final CapitalFactAssembler facts;
    private final MarketIntelRefreshRunRepository runs;
    private final CapitalFactorEvaluationService evaluationService;
    private final CapitalBehaviorEvaluationRepository evaluations;

    @Resource(name = "marketIntelRefreshExecutor")
    private Executor executor;

    public MarketIntelRefreshCoordinator(MarketIntelCapitalService capital,
                                         MarketDataGateway gateway,
                                         CapitalFlowRepository flows,
                                         CapitalBehaviorSignalService signalService,
                                         CapitalBehaviorSnapshotFactory snapshotsFactory,
                                         CapitalBehaviorSnapshotRepository snapshots,
                                         CapitalRuleExplanationService ruleService,
                                         CapitalInterpretationRepository interpretations,
                                         CapitalFactAssembler facts,
                                         MarketIntelRefreshRunRepository runs,
                                         CapitalFactorEvaluationService evaluationService,
                                         CapitalBehaviorEvaluationRepository evaluations) {
        this.capital = capital;
        this.gateway = gateway;
        this.flows = flows;
        this.signalService = signalService;
        this.snapshotsFactory = snapshotsFactory;
        this.snapshots = snapshots;
        this.ruleService = ruleService;
        this.interpretations = interpretations;
        this.facts = facts;
        this.runs = runs;
        this.evaluationService = evaluationService;
        this.evaluations = evaluations;
    }

    public MarketIntelRefreshRun requestRefresh(Long instrumentId) {
        Instrument instrument = capital.stock(instrumentId);
        MarketIntelRefreshRun run = runs.createRun(instrumentId, "MANUAL");
        try {
            executor.execute(() -> refresh(run, instrument));
        } catch (RuntimeException error) {
            runs.finishRun(run.getId(), MarketIntelRefreshRun.Status.FAILED, 0, 1);
            throw error;
        }
        return run;
    }

    void refresh(MarketIntelRefreshRun run, Instrument instrument) {
        CapitalFlowGatewayResult routed = gateway.fetchCapitalFlow(instrument, LocalDate.now());
        String sourceCode = routed.getSourceCode() == null || routed.getSourceCode().trim().isEmpty()
                ? "MARKET_DATA_GATEWAY" : routed.getSourceCode();
        MarketIntelRefreshStep step = runs.createStep(run.getId(), "CAPITAL_FLOW", sourceCode, 1);
        runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.RUNNING, 0, null, null);
        try {
            if (routed.getData() == null) {
                finishUnavailable(run, instrument, step, routed);
                return;
            }
            persistFresh(run, instrument, step, routed);
        } catch (RuntimeException error) {
            runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.FAILED, 0,
                    "INTERNAL_ERROR", error.getMessage());
            runs.finishRun(run.getId(), MarketIntelRefreshRun.Status.FAILED, 0, 1);
        }
    }

    private void finishUnavailable(MarketIntelRefreshRun run, Instrument instrument,
                                   MarketIntelRefreshStep step, CapitalFlowGatewayResult routed) {
        String warning = routed.getWarning() == null
                ? "资金流在线数据源不可用" : routed.getWarning();
        if (snapshots.findLatest(instrument.getId()).isPresent()) {
            runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.SKIPPED, 0,
                    MarketDataQualityStatus.STALE_FALLBACK.name(), warning);
            runs.finishRun(run.getId(), MarketIntelRefreshRun.Status.PARTIAL, 0, 0);
            return;
        }
        runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.FAILED, 0,
                routed.getErrorType() == null ? MarketDataQualityStatus.UNAVAILABLE.name() : routed.getErrorType(),
                warning);
        runs.finishRun(run.getId(), MarketIntelRefreshRun.Status.FAILED, 0, 1);
    }

    private void persistFresh(MarketIntelRefreshRun run, Instrument instrument,
                              MarketIntelRefreshStep step, CapitalFlowGatewayResult routed) {
        CapitalFlowData data = applyFallbacks(instrument, routed.getData());
        List<CapitalFlowPoint> points = data.allPoints();
        if (points.isEmpty()) {
            runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.EMPTY, 0, null, null);
            runs.finishRun(run.getId(), MarketIntelRefreshRun.Status.PARTIAL, 0, 0);
            return;
        }
        List<String> warnings = new ArrayList<String>(
                MarketIntelWarnings.merge(data.getWarnings(), routed.getWarning()));
        if (points.stream().anyMatch(point -> !"COMPLETE".equals(point.getQualityStatus()))
                && !warnings.contains("部分时间点行情未与资金流对齐")) {
            warnings.add("部分时间点行情未与资金流对齐");
        }
        flows.saveAll(points);
        List<CapitalBehaviorSignal> signals = signalService.detect(points);
        CapitalBehaviorSnapshot snapshot = snapshots.save(
                snapshotsFactory.create(instrument.getId(), points, signals, warnings));
        String evaluationWarning = persistEvaluation(snapshot);
        if (evaluationWarning != null) {
            warnings.add(evaluationWarning);
        }
        snapshot.setWarnings(warnings);
        snapshot.setQualityStatus(warnings.isEmpty() ? "COMPLETE" : "PARTIAL");
        // INSERT OR IGNORE 可能复用同一事实快照，因此成功和失败都要同步评价警告状态。
        snapshots.updateWarnings(snapshot.getId(), snapshot.getQualityStatus(), snapshot.getWarnings());
        persistRule(snapshot, ruleService.explain(points, signals));
        boolean partial = !warnings.isEmpty();
        runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.SUCCEEDED, points.size(),
                partial ? "PARTIAL_DATA" : null, partial ? String.join("；", warnings) : null);
        runs.finishRun(run.getId(), partial ? MarketIntelRefreshRun.Status.PARTIAL
                : MarketIntelRefreshRun.Status.SUCCEEDED, 1, 0);
    }

    private String persistEvaluation(CapitalBehaviorSnapshot snapshot) {
        try {
            CapitalBehaviorEvaluation evaluation = evaluationService.evaluate(snapshot);
            if (evaluation == null) {
                return "历史评价暂不可用，本次资金快照仍已更新";
            }
            evaluations.save(evaluation);
            return null;
        } catch (RuntimeException error) {
            // 历史评价是研究增强能力，不得反向阻断资金快照主链路。
            log.warn("capital history evaluation failed for snapshot={}", snapshot.getId(), error);
            return "历史评价暂不可用，本次资金快照仍已更新";
        }
    }

    private CapitalFlowData applyFallbacks(Instrument instrument, CapitalFlowData fresh) {
        List<CapitalFlowPoint> minutes = new ArrayList<CapitalFlowPoint>(fresh.getMinutePoints());
        List<CapitalFlowPoint> days = new ArrayList<CapitalFlowPoint>(fresh.getDailyPoints());
        List<String> warnings = new ArrayList<String>(fresh.getWarnings());
        applyQuoteFallback(instrument, minutes, days, warnings);
        if (days.isEmpty()) {
            List<CapitalFlowPoint> storedDays = latestStoredDays(instrument.getId());
            if (!storedDays.isEmpty()) {
                days.addAll(storedDays);
                removeWarnings(warnings, "HISTORICAL_FUND_FLOW_UNAVAILABLE", "DAILY_MARKET_UNAVAILABLE");
                LocalDate latest = storedDays.get(storedDays.size() - 1).getDataDate();
                warnings.add("历史资金流刷新失败，已使用最近成功数据（截至 " + latest + "）");
            }
        }
        return new CapitalFlowData(minutes, days, fresh.getTurnoverRate(), fresh.getVolumeRatio(),
                warnings, fresh.getProviderCode());
    }

    private void applyQuoteFallback(Instrument instrument, List<CapitalFlowPoint> minutes,
                                    List<CapitalFlowPoint> days, List<String> warnings) {
        if (!hasWarning(warnings, "QUOTE_UNAVAILABLE")) return;
        try {
            QuoteGatewayResult result = gateway.fetchQuotes("STOCK",
                    Collections.singletonList(instrument.getCode()), true);
            if (!isFresh(result.getQualityStatus())) return;
            Quote quote = result.getQuotes().stream().filter(Quote::isValid).findFirst().orElse(null);
            if (quote == null) return;
            CapitalFlowPoint target = latestPointForDate(minutes, quote.getAsOf());
            if (target == null) target = latestPointForDate(days, quote.getAsOf());
            if (target == null) return;
            if (quote.getPrice() != null) target.setPrice(decimal(quote.getPrice()));
            if (quote.getVolume() != null) target.setTradeVolume(decimal(quote.getVolume()));
            if (quote.getTurnover() != null) target.setCumulativeTradeAmount(decimal(quote.getTurnover()));
            removeWarnings(warnings, "QUOTE_UNAVAILABLE");
        } catch (RuntimeException ignored) {
            // 备用报价失败时保留原始告警；资金流主链路仍可继续保存。
        }
    }

    private CapitalFlowPoint latestPointForDate(List<CapitalFlowPoint> points, LocalDateTime asOf) {
        return points.stream()
                .filter(value -> asOf == null || value.getDataDate() == null
                        || value.getDataDate().equals(asOf.toLocalDate()))
                .max(Comparator.comparing(CapitalFlowPoint::getObservedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<CapitalFlowPoint> latestStoredDays(Long instrumentId) {
        Map<LocalDateTime, CapitalFlowPoint> distinct = new LinkedHashMap<LocalDateTime, CapitalFlowPoint>();
        for (CapitalFlowPoint point : flows.findLatestByGranularity(instrumentId, "DAY_1", 60)) {
            if (point.getObservedAt() != null && !distinct.containsKey(point.getObservedAt())) {
                distinct.put(point.getObservedAt(), point);
                if (distinct.size() == 20) break;
            }
        }
        List<CapitalFlowPoint> result = new ArrayList<CapitalFlowPoint>(distinct.values());
        result.sort(Comparator.comparing(CapitalFlowPoint::getObservedAt));
        return result;
    }

    private boolean hasWarning(List<String> warnings, String prefix) {
        return warnings.stream().anyMatch(value -> value != null && value.startsWith(prefix));
    }

    private boolean isFresh(MarketDataQualityStatus status) {
        return status == MarketDataQualityStatus.FRESH_PRIMARY
                || status == MarketDataQualityStatus.FRESH_FALLBACK
                || status == MarketDataQualityStatus.PARTIAL_FRESH;
    }

    private void removeWarnings(List<String> warnings, String... prefixes) {
        warnings.removeIf(value -> {
            if (value == null) return false;
            for (String prefix : prefixes) if (value.startsWith(prefix)) return true;
            return false;
        });
    }

    private BigDecimal decimal(Double value) {
        return new BigDecimal(String.valueOf(value));
    }

    private void persistRule(CapitalBehaviorSnapshot snapshot, CapitalRuleExplanation rule) {
        if (interpretations.findByAction(snapshot.getId(), "RULE", snapshot.getFingerprint()).isPresent()) return;
        CapitalInterpretation value = new CapitalInterpretation();
        value.setInstrumentId(snapshot.getInstrumentId());
        value.setSnapshotId(snapshot.getId());
        value.setInterpretationType("RULE");
        value.setStatus("SUCCEEDED");
        value.setPlainSummary(rule.getSummary());
        value.setFacts(facts.assemble(snapshot));
        value.setHypotheses(Collections.emptyList());
        value.setDataGaps(rule.getDataGaps());
        value.setObservationPoints(Collections.singletonList("观察后续资金与成交是否延续当前组合。"));
        value.setDisclaimer("规则解释仅用于研究，不构成投资建议。");
        value.setRuleVersion(rule.getRuleVersion());
        value.setInputHash(snapshot.getFingerprint());
        interpretations.save(value);
    }
}
