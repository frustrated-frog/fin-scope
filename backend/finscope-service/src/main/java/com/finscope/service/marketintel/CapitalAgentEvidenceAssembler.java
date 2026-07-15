package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalEvidenceRef;
import com.finscope.domain.marketintel.CapitalFactorObservation;
import com.finscope.domain.marketintel.CapitalFactorResult;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import com.finscope.domain.marketintel.CapitalWatchCondition;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import com.finscope.service.marketintel.factor.CapitalFactorEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将一次行情快照提升为可审计、可复现、可验证的 Agent 输入。
 */
@Service
public class CapitalAgentEvidenceAssembler {
    public static final String PROMPT_VERSION = "capital-interpret-v3";
    private static final int MINIMUM_COVERAGE_DIMENSIONS = 3;
    private static final int MINIMUM_FACTORS_FOR_DEGRADED_COVERAGE = 4;
    private static final List<String> ALLOWED_HYPOTHESES = Collections.unmodifiableList(Arrays.asList(
            "ACCUMULATION", "DISTRIBUTION", "ORDER_SPLITTING", "HIDDEN_FLOW", "LIQUIDITY_SHIFT"));

    private final CapitalFactorEngine factorEngine;
    private final CapitalBehaviorSignalService signalService;
    private final CapitalMetricCatalog metricCatalog;

    public CapitalAgentEvidenceAssembler(CapitalFactorEngine factorEngine,
                                         CapitalBehaviorSignalService signalService,
                                         CapitalMetricCatalog metricCatalog) {
        this.factorEngine = factorEngine;
        this.signalService = signalService;
        this.metricCatalog = metricCatalog;
    }

    public CapitalAgentEvidencePacket assemble(CapitalBehaviorSnapshot snapshot,
                                               CapitalRuleExplanation rules) {
        return assemble(snapshot, rules, null);
    }

    public CapitalAgentEvidencePacket assemble(CapitalBehaviorSnapshot snapshot,
                                               CapitalRuleExplanation rules,
                                               CapitalBehaviorEvaluation evaluation) {
        CapitalFactorResult factors = factorEngine.calculate(snapshot.getFacts());
        List<CapitalBehaviorSignal> signals = signalService.detect(factors);
        List<CapitalWatchCondition> watchConditions = signalService.watchConditions(factors);
        List<CapitalEvidenceRef> rawMetrics = rawMetrics(snapshot.getFacts());
        List<CapitalSignalEvaluation> historicalEvaluations = evaluation == null
                ? Collections.emptyList() : evaluation.getSignals().stream()
                .filter(CapitalSignalEvaluation::eligibleForAgent)
                .collect(Collectors.toList());
        List<String> dataGaps = dataGaps(snapshot, rules, factors, evaluation);
        List<CapitalFactorObservation> usableFactors = factors.getObservations().stream()
                .filter(value -> "COMPLETE".equals(value.getQualityStatus()))
                .collect(Collectors.toList());
        List<String> coverage = usableFactors.stream()
                .map(CapitalFactorObservation::getCategory)
                .filter(value -> value != null && !value.trim().isEmpty())
                .distinct().sorted().collect(Collectors.toList());
        boolean sufficientCoverage = coverage.size() >= MINIMUM_COVERAGE_DIMENSIONS
                || (!coverage.isEmpty() && usableFactors.size() >= MINIMUM_FACTORS_FOR_DEGRADED_COVERAGE);
        String ruleVersion = rules == null || rules.getRuleVersion() == null
                ? "capital-rules-v2" : rules.getRuleVersion();
        String fingerprint = evidenceFingerprint(snapshot, factors, signals, watchConditions,
                rawMetrics, historicalEvaluations, dataGaps, ruleVersion);
        return new CapitalAgentEvidencePacket(snapshot.getId(), snapshot.getInstrumentId(), snapshot.getAsOf(),
                snapshot.getFingerprint(), fingerprint, snapshot.getQualityStatus(), factors.getFactorVersion(),
                CapitalBehaviorSignalService.VERSION, ruleVersion, PROMPT_VERSION,
                factors.getObservations(), signals, rawMetrics, historicalEvaluations, ALLOWED_HYPOTHESES,
                watchConditions, dataGaps, coverage, sufficientCoverage);
    }

    private List<CapitalEvidenceRef> rawMetrics(List<CapitalFlowPoint> facts) {
        if (facts == null || facts.isEmpty()) return Collections.emptyList();
        List<CapitalFlowPoint> sorted = new ArrayList<CapitalFlowPoint>(facts);
        sorted.sort(Comparator.comparing(CapitalFlowPoint::getObservedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        CapitalFlowPoint latestDaily = latest(sorted, "DAY_1");
        CapitalFlowPoint latestMinute = sorted.stream()
                .filter(value -> value.getGranularity() != null && value.getGranularity().startsWith("MINUTE_"))
                .reduce((first, second) -> second).orElse(null);
        List<CapitalEvidenceRef> result = new ArrayList<CapitalEvidenceRef>();
        result.addAll(metricCatalog.evidence(latestDaily));
        if (latestMinute != null && (latestDaily == null || !latestMinute.getId().equals(latestDaily.getId()))) {
            result.addAll(metricCatalog.evidence(latestMinute));
        }
        return Collections.unmodifiableList(result);
    }

    private CapitalFlowPoint latest(List<CapitalFlowPoint> values, String granularity) {
        return values.stream().filter(value -> granularity.equals(value.getGranularity()))
                .reduce((first, second) -> second).orElse(null);
    }

    private List<String> dataGaps(CapitalBehaviorSnapshot snapshot, CapitalRuleExplanation rules,
                                  CapitalFactorResult factors, CapitalBehaviorEvaluation evaluation) {
        Set<String> result = new LinkedHashSet<String>();
        result.addAll(factors.getDataGaps());
        if (rules != null) result.addAll(rules.getDataGaps());
        if (evaluation != null) result.addAll(evaluation.getDataGaps());
        result.addAll(snapshot.getWarnings());
        if (!"COMPLETE".equals(snapshot.getQualityStatus())) {
            result.add("行情快照并非完整状态，结论仅基于当前可用证据。");
        }
        return Collections.unmodifiableList(new ArrayList<String>(result));
    }

    private String evidenceFingerprint(CapitalBehaviorSnapshot snapshot, CapitalFactorResult factors,
                                       List<CapitalBehaviorSignal> signals,
                                       List<CapitalWatchCondition> watchConditions,
                                       List<CapitalEvidenceRef> rawMetrics,
                                       List<CapitalSignalEvaluation> historicalEvaluations,
                                       List<String> dataGaps, String ruleVersion) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(snapshot.getFingerprint()).append('|').append(snapshot.getQualityStatus())
                .append('|').append(factors.getFactorVersion()).append('|')
                .append(CapitalBehaviorSignalService.VERSION).append('|').append(ruleVersion)
                .append('|').append(PROMPT_VERSION);
        factors.getObservations().stream().sorted(Comparator.comparing(CapitalFactorObservation::getFactorCode))
                .forEach(item -> canonical.append("|f:").append(item.factorRef()).append('=')
                        .append(item.getValue()).append(':').append(item.getState()).append(':')
                        .append(item.getQualityStatus()));
        signals.stream().sorted(Comparator.comparing(CapitalBehaviorSignal::getType))
                .forEach(item -> canonical.append("|s:").append(item.getType()).append(':')
                        .append(String.join(",", item.getFactorRefs())));
        watchConditions.stream().sorted(Comparator.comparing(CapitalWatchCondition::getId))
                .forEach(item -> canonical.append("|w:").append(item.getId()).append(':')
                        .append(item.getOperator()).append(':').append(item.getThreshold()));
        rawMetrics.stream().sorted(Comparator.comparing(CapitalEvidenceRef::getRef))
                .forEach(item -> canonical.append("|m:").append(item.getRef()).append('=')
                        .append(item.getValue()));
        historicalEvaluations.stream().sorted(Comparator.comparing(CapitalSignalEvaluation::evaluationRef))
                .forEach(item -> canonical.append("|e:").append(item.evaluationRef()).append('=')
                        .append(item.getSampleCount()).append(':').append(item.getAverageReturn())
                        .append(':').append(item.getMedianReturn()).append(':').append(item.getPositiveRate())
                        .append(':').append(item.getAverageMfe()).append(':').append(item.getAverageMae())
                        .append(':').append(item.getStabilityStatus()).append(':')
                        .append(item.getEvaluationStatus()));
        dataGaps.stream().sorted().forEach(item -> canonical.append("|g:").append(item));
        return JdkFinanceHttpClient.sha256(canonical.toString());
    }
}
