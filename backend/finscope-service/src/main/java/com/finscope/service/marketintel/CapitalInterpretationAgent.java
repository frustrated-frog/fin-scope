package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CapitalInterpretationAgent {
    private static final int PRIMARY_TIMEOUT_MS = 60_000;
    private static final int REPAIR_TIMEOUT_MS = 30_000;
    private static final int MAXIMUM_REQUIRED_OUTPUT_DIMENSIONS = 3;
    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final CapitalAgentResponseParser parser;
    private final CapitalInterpretationGate gate;

    public CapitalInterpretationAgent(LlmChatClient llm, ObjectMapper json,
                                      CapitalAgentResponseParser parser,
                                      CapitalInterpretationGate gate) {
        this.llm = llm;
        this.json = json;
        this.parser = parser;
        this.gate = gate;
    }

    public CapitalInterpretation interpret(CapitalAgentEvidencePacket packet,
                                            CapitalRuleExplanation rules) {
        if (!packet.isSufficientCoverage()) {
            return fallback(packet, rules, "INSUFFICIENT_DATA", "INSUFFICIENT_FACTOR_COVERAGE");
        }
        if (!llm.isConfigured()) return fallback(packet, rules, "FALLBACK", "LLM_NOT_CONFIGURED");
        String output = null;
        try {
            output = llm.complete(systemPrompt(packet), input(packet), PRIMARY_TIMEOUT_MS);
            JsonNode root;
            try {
                root = parser.parse(output);
            } catch (Exception firstFailure) {
                output = llm.complete(repairPrompt(), output == null ? "" : output, REPAIR_TIMEOUT_MS);
                root = parser.parse(output);
            }
            CapitalInterpretationGate.Result accepted = gate.apply(root, packet);
            long acceptedDimensions = accepted.observations.stream()
                    .map(item -> item.getDimension()).distinct().count();
            int requiredDimensions = requiredOutputDimensions(packet);
            if (acceptedDimensions < requiredDimensions) {
                List<String> reasons = new ArrayList<String>(accepted.rejectionReasons);
                reasons.add("模型输出未覆盖至少" + requiredDimensions + "个可用分析维度");
                CapitalInterpretation rejected = fallback(packet, rules, "FALLBACK", "OUTPUT_REJECTED_BY_GATE");
                rejected.setRejectedOutputCount(reasons.size());
                rejected.setRejectionReasons(reasons);
                rejected.setOutputHash(JdkFinanceHttpClient.sha256(output));
                return rejected;
            }
            CapitalInterpretation result = base(packet, rules);
            result.setStatus("SUCCEEDED");
            result.setMarketState(accepted.marketState);
            result.setExecutiveSummary(accepted.executiveSummary);
            result.setPlainSummary(accepted.executiveSummary);
            result.setObservations(accepted.observations);
            result.setHypotheses(accepted.hypotheses);
            result.setCounterEvidence(accepted.counterEvidence);
            result.setWatchConditionRefs(accepted.watchConditionRefs);
            result.setDataGaps(union(packet.getDataGaps(), accepted.dataGaps));
            result.setConfidence(!"COMPLETE".equals(packet.getQualityStatus())
                    || packet.getCoverageDimensions().size() < MAXIMUM_REQUIRED_OUTPUT_DIMENSIONS
                    ? "LOW" : accepted.confidence);
            result.setDisclaimer(accepted.disclaimer);
            result.setRejectedOutputCount(accepted.rejectionReasons.size());
            result.setRejectionReasons(accepted.rejectionReasons);
            result.setOutputHash(JdkFinanceHttpClient.sha256(output));
            return result;
        } catch (SocketTimeoutException e) {
            return fallback(packet, rules, "FALLBACK", "LLM_TIMEOUT");
        } catch (Exception e) {
            return fallback(packet, rules, "FALLBACK", "INVALID_MODEL_OUTPUT");
        }
    }

    private CapitalInterpretation base(CapitalAgentEvidencePacket packet, CapitalRuleExplanation rules) {
        CapitalInterpretation value = new CapitalInterpretation();
        value.setInstrumentId(packet.getInstrumentId());
        value.setSnapshotId(packet.getSnapshotId());
        value.setInterpretationType("AGENT");
        value.setRuleVersion(packet.getRuleVersion());
        value.setModelName(llm.modelName());
        value.setPromptVersion(packet.getPromptVersion());
        value.setInputHash(packet.getEvidenceFingerprint());
        value.setFactorVersion(packet.getFactorVersion());
        value.setSignalVersion(packet.getSignalVersion());
        value.setEvidenceRefs(packet.getRawMetrics());
        List<String> facts = new ArrayList<String>();
        packet.getFactorObservations().forEach(item -> facts.add(item.getLabel() + "："
                + item.getValue() + (item.getState() == null ? "" : "（" + item.getState() + "）")));
        value.setFacts(facts);
        return value;
    }

    private CapitalInterpretation fallback(CapitalAgentEvidencePacket packet,
                                           CapitalRuleExplanation rules,
                                           String status, String reason) {
        CapitalInterpretation value = base(packet, rules);
        value.setStatus(status);
        value.setMarketState("INSUFFICIENT_DATA".equals(status) ? "INSUFFICIENT_DATA" : "NEUTRAL");
        value.setExecutiveSummary(rules.getSummary());
        value.setPlainSummary(rules.getSummary());
        value.setHypotheses(Collections.emptyList());
        value.setObservations(Collections.emptyList());
        value.setCounterEvidence(Collections.emptyList());
        value.setDataGaps(packet.getDataGaps());
        value.setWatchConditionRefs(packet.getWatchConditions().stream()
                .map(item -> item.getId()).collect(java.util.stream.Collectors.toList()));
        value.setObservationPoints(Collections.singletonList("继续观察量能、换手、主力净流向和日内资金方向是否连续。"));
        value.setConfidence("LOW");
        value.setDisclaimer("规则解释和模型假设仅用于研究，不构成投资建议。");
        value.setFallbackReason(reason);
        return value;
    }

    private String systemPrompt(CapitalAgentEvidencePacket packet) {
        int requiredDimensions = requiredOutputDimensions(packet);
        return "你是A股资金行为研究Agent。只能使用输入中的factorRef、metricRef和watch id；"
                + "输出单个JSON对象，字段必须为marketState、executiveSummary、observations、hypotheses、"
                + "counterEvidence、watchConditionRefs、dataGaps、confidence、disclaimer。"
                + "observations每项必须含dimension、claim、factorRefs、metricRefs，并覆盖至少"
                + requiredDimensions + "个输入中实际可用的不同维度。"
                + "文本只能复述证据包已有数字，不得自行计算或创造数值，不得输出买卖建议；"
                + "拆单和隐藏资金只能是LOW，整体置信度只能LOW或MID。";
    }

    private int requiredOutputDimensions(CapitalAgentEvidencePacket packet) {
        return Math.max(1, Math.min(MAXIMUM_REQUIRED_OUTPUT_DIMENSIONS,
                packet.getCoverageDimensions().size()));
    }

    private String repairPrompt() {
        return "把输入修复成符合上一个资金行为JSON契约的单个JSON对象。不得补充新事实或新引用，只输出JSON。";
    }

    private String input(CapitalAgentEvidencePacket packet) throws Exception {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("snapshotId", packet.getSnapshotId());
        value.put("asOf", packet.getAsOf() == null ? null : packet.getAsOf().toString());
        value.put("qualityStatus", packet.getQualityStatus());
        value.put("factorVersion", packet.getFactorVersion());
        value.put("signalVersion", packet.getSignalVersion());
        List<Map<String, Object>> factorValues = new ArrayList<Map<String, Object>>();
        packet.getFactorObservations().forEach(item -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("factorRef", item.factorRef());
            row.put("code", item.getFactorCode());
            row.put("label", item.getLabel());
            row.put("category", item.getCategory());
            row.put("window", item.getWindow());
            row.put("value", item.getValue());
            row.put("baseline", item.getBaseline());
            row.put("percentile", item.getPercentile());
            row.put("zScore", item.getZScore());
            row.put("state", item.getState());
            row.put("qualityStatus", item.getQualityStatus());
            row.put("metricRefs", item.getMetricRefs());
            row.put("boundary", item.getInterpretationBoundary());
            factorValues.add(row);
        });
        List<Map<String, Object>> signalValues = new ArrayList<Map<String, Object>>();
        packet.getSignals().forEach(item -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("type", item.getType());
            row.put("label", item.getLabel());
            row.put("window", item.getWindow());
            row.put("factorRefs", item.getFactorRefs());
            row.put("metricRefs", item.getMetricRefs());
            row.put("actualValues", item.getActualValues());
            row.put("thresholds", item.getThresholds());
            row.put("qualityStatus", item.getQualityStatus());
            signalValues.add(row);
        });
        List<Map<String, Object>> metricValues = new ArrayList<Map<String, Object>>();
        packet.getRawMetrics().forEach(item -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("ref", item.getRef());
            row.put("label", item.getLabel());
            row.put("category", item.getCategory());
            row.put("value", item.getValue());
            row.put("unit", item.getUnit());
            row.put("observedAt", item.getObservedAt() == null ? null : item.getObservedAt().toString());
            metricValues.add(row);
        });
        List<Map<String, Object>> watchValues = new ArrayList<Map<String, Object>>();
        packet.getWatchConditions().forEach(item -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", item.getId());
            row.put("label", item.getLabel());
            row.put("factorRef", item.getFactorRef());
            row.put("operator", item.getOperator());
            row.put("threshold", item.getThreshold());
            row.put("unit", item.getUnit());
            watchValues.add(row);
        });
        value.put("factors", factorValues);
        value.put("signals", signalValues);
        value.put("rawMetrics", metricValues);
        value.put("allowedHypotheses", packet.getAllowedHypotheses());
        value.put("watchConditions", watchValues);
        value.put("dataGaps", packet.getDataGaps());
        return json.writeValueAsString(value);
    }

    private List<String> union(List<String> first, List<String> second) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<String>();
        result.addAll(first);
        result.addAll(second);
        return Collections.unmodifiableList(new ArrayList<String>(result));
    }
}
