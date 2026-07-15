package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalHypothesis;
import com.finscope.domain.marketintel.CapitalInterpretationObservation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 服务端证据门禁：模型只能组织已计算证据，不能创造因子、指标或观察条件。 */
@Component
public class CapitalInterpretationGate {
    private static final Set<String> STATES = new HashSet<String>(Arrays.asList(
            "VOLUME_EXPANSION_OUTFLOW", "VOLUME_EXPANSION_INFLOW", "PRICE_FLOW_DIVERGENCE",
            "MIXED", "NEUTRAL", "INTRADAY_REVERSAL", "INSUFFICIENT_DATA"));
    private static final Set<String> DIMENSIONS = new HashSet<String>(Arrays.asList(
            "VOLUME", "TURNOVER", "FLOW", "ORDER_STRUCTURE", "ALIGNMENT", "INTRADAY"));

    public Result apply(JsonNode root, CapitalAgentEvidencePacket packet) {
        requireObject(root);
        String marketState = required(root, "marketState");
        if (!STATES.contains(marketState)) throw new IllegalArgumentException("unknown market state");
        String summary = required(root, "executiveSummary");
        String confidence = required(root, "confidence").toUpperCase();
        if (!Arrays.asList("LOW", "MID").contains(confidence)) confidence = "MID";
        String disclaimer = required(root, "disclaimer");
        Set<String> factorRefs = packet.getFactorObservations().stream()
                .map(item -> item.factorRef()).collect(Collectors.toSet());
        Set<String> metricRefs = packet.getRawMetrics().stream()
                .map(item -> item.getRef()).collect(Collectors.toSet());
        Set<String> watchRefs = packet.getWatchConditions().stream()
                .map(item -> item.getId()).collect(Collectors.toSet());

        List<CapitalInterpretationObservation> observations = new ArrayList<CapitalInterpretationObservation>();
        List<String> rejections = new ArrayList<String>();
        JsonNode observationNodes = array(root, "observations");
        for (JsonNode node : observationNodes) {
            String dimension = required(node, "dimension");
            List<String> nodeFactors = strings(node.path("factorRefs"));
            List<String> nodeMetrics = strings(node.path("metricRefs"));
            if (!DIMENSIONS.contains(dimension) || nodeFactors.isEmpty()
                    || !factorRefs.containsAll(nodeFactors) || !metricRefs.containsAll(nodeMetrics)) {
                rejections.add("观察项引用了未知维度、因子或指标");
                continue;
            }
            CapitalInterpretationObservation value = new CapitalInterpretationObservation();
            value.setDimension(dimension);
            value.setClaim(required(node, "claim"));
            value.setFactorRefs(nodeFactors);
            value.setMetricRefs(nodeMetrics);
            observations.add(value);
        }
        List<String> acceptedWatchRefs = strings(array(root, "watchConditionRefs")).stream()
                .filter(watchRefs::contains).collect(Collectors.toList());
        int rejectedWatchRefs = strings(array(root, "watchConditionRefs")).size() - acceptedWatchRefs.size();
        for (int i = 0; i < rejectedWatchRefs; i++) rejections.add("观察条件引用不存在");
        List<CapitalHypothesis> hypotheses = hypotheses(array(root, "hypotheses"), packet, metricRefs, rejections);
        return new Result(marketState, summary, observations, hypotheses,
                strings(array(root, "counterEvidence")), acceptedWatchRefs,
                strings(array(root, "dataGaps")), confidence, disclaimer, rejections);
    }

    private List<CapitalHypothesis> hypotheses(JsonNode nodes, CapitalAgentEvidencePacket packet,
                                                Set<String> metricRefs, List<String> rejections) {
        List<CapitalHypothesis> result = new ArrayList<CapitalHypothesis>();
        for (JsonNode node : nodes) {
            String type = required(node, "type");
            List<String> refs = strings(node.path("supportingMetricRefs"));
            if (!packet.getAllowedHypotheses().contains(type) || refs.isEmpty() || !metricRefs.containsAll(refs)) {
                rejections.add("假设类型或证据引用不在允许范围内");
                continue;
            }
            CapitalHypothesis value = new CapitalHypothesis();
            value.setType(type);
            value.setClaim(required(node, "claim"));
            String requested = required(node, "confidence");
            value.setConfidence(("ORDER_SPLITTING".equals(type) || "HIDDEN_FLOW".equals(type))
                    ? "LOW" : ("LOW".equalsIgnoreCase(requested) ? "LOW" : "MID"));
            value.setSupportingMetricRefs(refs);
            value.setCounterEvidence(strings(node.path("counterEvidence")));
            value.setDataGaps(strings(node.path("dataGaps")));
            if ("ORDER_SPLITTING".equals(type) || "HIDDEN_FLOW".equals(type)) {
                List<String> counter = new ArrayList<String>(value.getCounterEvidence());
                counter.add("缺少 Level-2 逐笔委托/成交，只能保留为低置信度行为假设。");
                value.setCounterEvidence(counter);
            }
            result.add(value);
        }
        return result;
    }

    private void requireObject(JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("expected object");
    }

    private JsonNode array(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) throw new IllegalArgumentException("expected array " + field);
        return value;
    }

    private String required(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty())
            throw new IllegalArgumentException("missing " + field);
        return value.asText().trim();
    }

    private List<String> strings(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) throw new IllegalArgumentException("expected string array");
        List<String> result = new ArrayList<String>();
        for (JsonNode node : nodes) {
            if (!node.isTextual()) throw new IllegalArgumentException("expected string");
            result.add(node.asText());
        }
        return result;
    }

    public static final class Result {
        public final String marketState;
        public final String executiveSummary;
        public final List<CapitalInterpretationObservation> observations;
        public final List<CapitalHypothesis> hypotheses;
        public final List<String> counterEvidence;
        public final List<String> watchConditionRefs;
        public final List<String> dataGaps;
        public final String confidence;
        public final String disclaimer;
        public final List<String> rejectionReasons;

        private Result(String marketState, String executiveSummary,
                       List<CapitalInterpretationObservation> observations,
                       List<CapitalHypothesis> hypotheses, List<String> counterEvidence,
                       List<String> watchConditionRefs, List<String> dataGaps,
                       String confidence, String disclaimer, List<String> rejectionReasons) {
            this.marketState = marketState;
            this.executiveSummary = executiveSummary;
            this.observations = Collections.unmodifiableList(observations);
            this.hypotheses = Collections.unmodifiableList(hypotheses);
            this.counterEvidence = Collections.unmodifiableList(counterEvidence);
            this.watchConditionRefs = Collections.unmodifiableList(watchConditionRefs);
            this.dataGaps = Collections.unmodifiableList(dataGaps);
            this.confidence = confidence;
            this.disclaimer = disclaimer;
            this.rejectionReasons = Collections.unmodifiableList(rejectionReasons);
        }
    }
}
