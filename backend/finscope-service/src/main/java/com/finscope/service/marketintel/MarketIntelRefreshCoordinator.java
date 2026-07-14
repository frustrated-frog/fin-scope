package com.finscope.service.marketintel;

import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;

/** 资金行为刷新编排；数据源选择、切换和熔断统一由 MarketDataGateway 负责。 */
@Service
public class MarketIntelRefreshCoordinator {
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
                                         MarketIntelRefreshRunRepository runs) {
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
        CapitalFlowData data = routed.getData();
        List<CapitalFlowPoint> points = data.allPoints();
        if (points.isEmpty()) {
            runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.EMPTY, 0, null, null);
            runs.finishRun(run.getId(), MarketIntelRefreshRun.Status.PARTIAL, 0, 0);
            return;
        }
        List<String> warnings = new ArrayList<String>(new LinkedHashSet<String>(data.getWarnings()));
        if (routed.getWarning() != null && !routed.getWarning().trim().isEmpty()
                && !warnings.contains(routed.getWarning())) {
            warnings.add(routed.getWarning());
        }
        if (points.stream().anyMatch(point -> !"COMPLETE".equals(point.getQualityStatus()))
                && !warnings.contains("部分时间点行情未与资金流对齐")) {
            warnings.add("部分时间点行情未与资金流对齐");
        }
        flows.saveAll(points);
        List<CapitalBehaviorSignal> signals = signalService.detect(points);
        CapitalBehaviorSnapshot snapshot = snapshots.save(
                snapshotsFactory.create(instrument.getId(), points, signals, warnings));
        persistRule(snapshot, ruleService.explain(points, signals));
        boolean partial = !warnings.isEmpty();
        runs.updateStep(step.getId(), MarketIntelRefreshStep.Status.SUCCEEDED, points.size(),
                partial ? "PARTIAL_DATA" : null, partial ? String.join("；", warnings) : null);
        runs.finishRun(run.getId(), partial ? MarketIntelRefreshRun.Status.PARTIAL
                : MarketIntelRefreshRun.Status.SUCCEEDED, 1, 0);
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
