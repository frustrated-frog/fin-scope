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
    private static final String SAFE_DISCLAIMER = "模型仅组织公开数据和已登记因子，仅用于研究，不构成投资建议。";
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
        List<String> validationReasons = new ArrayList<String>();
        try {
            output = llm.complete(systemPrompt(packet), input(packet), PRIMARY_TIMEOUT_MS);
            try {
                return interpretOutput(output, packet, rules);
            } catch (ModelOutputException firstFailure) {
                validationReasons.add(validationReason("首次输出", firstFailure));
            }
            output = llm.complete(repairPrompt(packet),
                    repairInput(packet, output, validationReasons.get(0)), REPAIR_TIMEOUT_MS);
            try {
                return interpretOutput(output, packet, rules);
            } catch (ModelOutputException repairFailure) {
                validationReasons.add(validationReason("修复输出", repairFailure));
                return invalidOutputFallback(packet, rules, output,
                        repairFailure.fallbackReason, validationReasons);
            }
        } catch (SocketTimeoutException e) {
            return fallback(packet, rules, "FALLBACK", "LLM_TIMEOUT");
        } catch (Exception e) {
            validationReasons.add("模型调用异常：" + e.getClass().getSimpleName());
            return invalidOutputFallback(packet, rules, output,
                    "INVALID_MODEL_OUTPUT", validationReasons);
        }
    }

    private CapitalInterpretation interpretOutput(String output, CapitalAgentEvidencePacket packet,
                                                   CapitalRuleExplanation rules) throws ModelOutputException {
        JsonNode root;
        try {
            root = parser.parse(output);
        } catch (Exception error) {
            throw new ModelOutputException("INVALID_MODEL_OUTPUT", message(error), error);
        }
        CapitalInterpretationGate.Result accepted;
        try {
            accepted = gate.apply(root, packet);
        } catch (Exception error) {
            throw new ModelOutputException("INVALID_MODEL_OUTPUT", message(error), error);
        }
        long acceptedDimensions = accepted.observations.stream()
                .map(item -> item.getDimension()).distinct().count();
        int requiredDimensions = requiredOutputDimensions(packet);
        if (acceptedDimensions < requiredDimensions) {
            List<String> reasons = new ArrayList<String>(accepted.rejectionReasons);
            reasons.add("模型输出未覆盖至少" + requiredDimensions + "个可用分析维度");
            throw new ModelOutputException("OUTPUT_REJECTED_BY_GATE", String.join("；", reasons), null);
        }
        CapitalInterpretation result = base(packet, rules);
        result.setStatus("SUCCEEDED");
        result.setMarketState(accepted.marketState);
        String summary = accepted.executiveSummary == null ? rules.getSummary() : accepted.executiveSummary;
        result.setExecutiveSummary(summary);
        result.setPlainSummary(summary);
        result.setObservations(accepted.observations);
        result.setHypotheses(accepted.hypotheses);
        result.setCounterEvidence(accepted.counterEvidence);
        result.setWatchConditionRefs(accepted.watchConditionRefs);
        result.setDataGaps(union(packet.getDataGaps(), accepted.dataGaps));
        result.setConfidence(!"COMPLETE".equals(packet.getQualityStatus())
                || packet.getCoverageDimensions().size() < MAXIMUM_REQUIRED_OUTPUT_DIMENSIONS
                ? "LOW" : accepted.confidence);
        result.setDisclaimer(accepted.disclaimer == null ? SAFE_DISCLAIMER : accepted.disclaimer);
        result.setRejectedOutputCount(accepted.rejectionReasons.size());
        result.setRejectionReasons(accepted.rejectionReasons);
        result.setOutputHash(JdkFinanceHttpClient.sha256(output));
        return result;
    }

    private CapitalInterpretation invalidOutputFallback(CapitalAgentEvidencePacket packet,
                                                        CapitalRuleExplanation rules,
                                                        String output, String reason,
                                                        List<String> validationReasons) {
        CapitalInterpretation rejected = fallback(packet, rules, "FALLBACK", reason);
        rejected.setRejectedOutputCount(validationReasons.size());
        rejected.setRejectionReasons(validationReasons);
        if (output != null && !output.trim().isEmpty()) {
            rejected.setOutputHash(JdkFinanceHttpClient.sha256(output));
        }
        return rejected;
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
                + contractSchema()
                + "文本只能复述证据包已有数字，不得自行计算或创造数值，不得输出买卖建议；"
                + "executiveSummary和disclaimer不要包含任何数字；"
                + "拆单和隐藏资金只能是LOW，整体置信度只能LOW或MID。";
    }

    private int requiredOutputDimensions(CapitalAgentEvidencePacket packet) {
        return Math.max(1, Math.min(MAXIMUM_REQUIRED_OUTPUT_DIMENSIONS,
                packet.getCoverageDimensions().size()));
    }

    private String repairPrompt(CapitalAgentEvidencePacket packet) {
        return systemPrompt(packet)
                + "你正在修复一份未通过服务端校验的输出。必须根据validationError纠正字段和值，"
                + "只能引用evidencePacket中的现有引用，只输出修复后的单个JSON对象。";
    }

    private String contractSchema() {
        return "marketState只能是VOLUME_EXPANSION_OUTFLOW、VOLUME_EXPANSION_INFLOW、"
                + "PRICE_FLOW_DIVERGENCE、MIXED、NEUTRAL、INTRADAY_REVERSAL或INSUFFICIENT_DATA；"
                + "dimension只能是VOLUME、TURNOVER、FLOW、ORDER_STRUCTURE、INTRADAY或MULTI_PERIOD；"
                + "hypotheses每项必须含type、claim、confidence、supportingMetricRefs、counterEvidence、dataGaps，"
                + "supportingMetricRefs只能引用rawMetrics.ref；counterEvidence和dataGaps必须是字符串数组；"
                + "顶层counterEvidence、dataGaps和watchConditionRefs也必须是字符串数组；";
    }

    private String repairInput(CapitalAgentEvidencePacket packet, String invalidOutput,
                               String validationError) throws Exception {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("validationError", validationError);
        value.put("invalidOutput", invalidOutput == null ? "" : invalidOutput);
        value.put("evidencePacket", json.readTree(input(packet)));
        return json.writeValueAsString(value);
    }

    private String validationReason(String stage, Exception error) {
        return stage + "未通过JSON契约校验：" + message(error);
    }

    private String message(Throwable error) {
        String value = error == null || error.getMessage() == null
                ? "未知校验错误" : error.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= 300 ? value : value.substring(0, 300);
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

    private static final class ModelOutputException extends Exception {
        private final String fallbackReason;

        private ModelOutputException(String fallbackReason, String message, Throwable cause) {
            super(message, cause);
            this.fallbackReason = fallbackReason;
        }
    }
}
