package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalEvidenceRef;
import com.finscope.domain.marketintel.CapitalFactorObservation;
import com.finscope.domain.marketintel.CapitalHypothesis;
import com.finscope.domain.marketintel.CapitalInterpretationObservation;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import com.finscope.domain.marketintel.CapitalWatchCondition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 服务端证据门禁：模型只能组织已计算证据，不能创造因子、指标或观察条件。 */
@Component
public class CapitalInterpretationGate {
    private static final Set<String> STATES = new HashSet<String>(Arrays.asList(
            "VOLUME_EXPANSION_OUTFLOW", "VOLUME_EXPANSION_INFLOW", "PRICE_FLOW_DIVERGENCE",
            "MIXED", "NEUTRAL", "INTRADAY_REVERSAL", "INSUFFICIENT_DATA"));
    private static final Set<String> DIMENSIONS = new HashSet<String>(Arrays.asList(
            "VOLUME", "TURNOVER", "FLOW", "ORDER_STRUCTURE", "INTRADAY", "MULTI_PERIOD"));
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z0-9_])[+-]?\\d+(?:\\.\\d+)?%?");

    public Result apply(JsonNode root, CapitalAgentEvidencePacket packet) {
        requireObject(root);
        String marketState = required(root, "marketState");
        if (!STATES.contains(marketState)) throw new IllegalArgumentException("unknown market state");
        List<String> rejections = new ArrayList<String>();
        String summary = required(root, "executiveSummary");
        Set<String> allowedNumbers = allowedNumbers(packet);
        Set<String> allHistoricalNumbers = historicalNumbers(packet, null);
        Set<String> unsafeSummaryNumbers = externalNumbers(summary, allowedNumbers);
        Set<String> unauditedSummaryNumbers = numbers(summary);
        unauditedSummaryNumbers.retainAll(allHistoricalNumbers);
        if (!unsafeSummaryNumbers.isEmpty() || !unauditedSummaryNumbers.isEmpty()) {
            rejections.add(!unauditedSummaryNumbers.isEmpty()
                    ? "摘要包含无法绑定引用的历史统计数字" + unauditedSummaryNumbers + "，已使用规则摘要替换"
                    : "摘要包含证据包之外的数字" + unsafeSummaryNumbers + "，已使用规则摘要替换");
            summary = null;
        }
        String confidence = required(root, "confidence").toUpperCase();
        if (!Arrays.asList("LOW", "MID").contains(confidence)) confidence = "MID";
        String disclaimer = required(root, "disclaimer");
        Set<String> unsafeDisclaimerNumbers = externalNumbers(disclaimer, allowedNumbers);
        Set<String> unauditedDisclaimerNumbers = numbers(disclaimer);
        unauditedDisclaimerNumbers.retainAll(allHistoricalNumbers);
        if (!unsafeDisclaimerNumbers.isEmpty() || !unauditedDisclaimerNumbers.isEmpty()) {
            rejections.add(!unauditedDisclaimerNumbers.isEmpty()
                    ? "免责声明包含无法绑定引用的历史统计数字" + unauditedDisclaimerNumbers
                    + "，已使用系统免责声明替换"
                    : "免责声明包含证据包之外的数字" + unsafeDisclaimerNumbers + "，已使用系统免责声明替换");
            disclaimer = null;
        }
        Set<String> factorRefs = packet.getFactorObservations().stream()
                .map(item -> item.factorRef()).collect(Collectors.toSet());
        Map<String, String> factorDimensions = packet.getFactorObservations().stream()
                .collect(Collectors.toMap(item -> item.factorRef(), item -> item.getCategory()));
        Set<String> metricRefs = packet.getRawMetrics().stream()
                .map(item -> item.getRef()).collect(Collectors.toSet());
        Set<String> evaluationRefs = packet.getHistoricalEvaluations().stream()
                .map(CapitalSignalEvaluation::evaluationRef).collect(Collectors.toSet());
        Set<String> watchRefs = packet.getWatchConditions().stream()
                .map(item -> item.getId()).collect(Collectors.toSet());

        List<CapitalInterpretationObservation> observations = new ArrayList<CapitalInterpretationObservation>();
        JsonNode observationNodes = array(root, "observations");
        for (JsonNode node : observationNodes) {
            String dimension = required(node, "dimension");
            String claim = required(node, "claim");
            List<String> nodeFactors = strings(node.path("factorRefs"));
            List<String> nodeMetrics = strings(node.path("metricRefs"));
            List<String> nodeEvaluations = optionalStrings(node.get("evaluationRefs"));
            boolean dimensionMatches = nodeFactors.stream()
                    .anyMatch(ref -> dimension.equals(factorDimensions.get(ref)));
            if (!DIMENSIONS.contains(dimension) || nodeFactors.isEmpty()
                    || !factorRefs.containsAll(nodeFactors) || !metricRefs.containsAll(nodeMetrics)
                    || !evaluationRefs.containsAll(nodeEvaluations)
                    || !dimensionMatches) {
                rejections.add("观察项引用了未知或不匹配的维度、因子、指标或历史评价");
                continue;
            }
            Set<String> claimedHistoricalNumbers = numbers(claim);
            claimedHistoricalNumbers.retainAll(allHistoricalNumbers);
            Set<String> citedHistoricalNumbers = historicalNumbers(packet,
                    new HashSet<String>(nodeEvaluations));
            if (!citedHistoricalNumbers.containsAll(claimedHistoricalNumbers)) {
                rejections.add("观察项复述了历史统计数字，但缺少对应的历史评价引用");
                continue;
            }
            Set<String> localAllowedNumbers = observationNumbers(packet, nodeFactors, nodeMetrics, nodeEvaluations);
            if (containsExternalNumber(claim, localAllowedNumbers)) {
                rejections.add("观察项包含所引用证据之外的数字");
                continue;
            }
            CapitalInterpretationObservation value = new CapitalInterpretationObservation();
            value.setDimension(dimension);
            value.setClaim(claim);
            value.setFactorRefs(nodeFactors);
            value.setMetricRefs(nodeMetrics);
            value.setEvaluationRefs(nodeEvaluations);
            observations.add(value);
        }
        List<String> acceptedWatchRefs = strings(array(root, "watchConditionRefs")).stream()
                .filter(watchRefs::contains).collect(Collectors.toList());
        int rejectedWatchRefs = strings(array(root, "watchConditionRefs")).size() - acceptedWatchRefs.size();
        for (int i = 0; i < rejectedWatchRefs; i++) rejections.add("观察条件引用不存在");
        List<CapitalHypothesis> hypotheses = hypotheses(array(root, "hypotheses"), packet, metricRefs,
                allowedNumbers, allHistoricalNumbers, rejections);
        return new Result(marketState, summary, observations, hypotheses,
                validatedTexts(array(root, "counterEvidence"), allowedNumbers, allHistoricalNumbers, rejections),
                acceptedWatchRefs,
                validatedTexts(array(root, "dataGaps"), allowedNumbers, allHistoricalNumbers, rejections),
                confidence, disclaimer, rejections);
    }

    private List<CapitalHypothesis> hypotheses(JsonNode nodes, CapitalAgentEvidencePacket packet,
                                                Set<String> metricRefs, Set<String> allowedNumbers,
                                                Set<String> historicalNumbers,
                                                List<String> rejections) {
        List<CapitalHypothesis> result = new ArrayList<CapitalHypothesis>();
        for (JsonNode node : nodes) {
            String type = required(node, "type");
            String claim = required(node, "claim");
            List<String> refs = strings(node.path("supportingMetricRefs"));
            if (!packet.getAllowedHypotheses().contains(type) || refs.isEmpty() || !metricRefs.containsAll(refs)) {
                rejections.add("假设类型或证据引用不在允许范围内");
                continue;
            }
            if (containsHistoricalNumber(claim, historicalNumbers)) {
                rejections.add("假设包含无法绑定引用的历史统计数字");
                continue;
            }
            if (containsExternalNumber(claim, allowedNumbers)) {
                rejections.add("假设包含证据包之外的数字");
                continue;
            }
            CapitalHypothesis value = new CapitalHypothesis();
            value.setType(type);
            value.setClaim(claim);
            String requested = required(node, "confidence");
            value.setConfidence(("ORDER_SPLITTING".equals(type) || "HIDDEN_FLOW".equals(type))
                    ? "LOW" : ("LOW".equalsIgnoreCase(requested) ? "LOW" : "MID"));
            value.setSupportingMetricRefs(refs);
            value.setCounterEvidence(validatedTexts(node.path("counterEvidence"), allowedNumbers,
                    historicalNumbers, rejections));
            value.setDataGaps(validatedTexts(node.path("dataGaps"), allowedNumbers,
                    historicalNumbers, rejections));
            if ("ORDER_SPLITTING".equals(type) || "HIDDEN_FLOW".equals(type)) {
                List<String> counter = new ArrayList<String>(value.getCounterEvidence());
                counter.add("缺少 Level-2 逐笔委托/成交，只能保留为低置信度行为假设。");
                value.setCounterEvidence(counter);
            }
            result.add(value);
        }
        return result;
    }

    private List<String> validatedTexts(JsonNode nodes, Set<String> allowedNumbers,
                                        Set<String> historicalNumbers,
                                        List<String> rejections) {
        List<String> result = new ArrayList<String>();
        for (String value : strings(nodes)) {
            if (containsHistoricalNumber(value, historicalNumbers)) {
                rejections.add("文本包含无法绑定引用的历史统计数字");
            } else if (containsExternalNumber(value, allowedNumbers)) {
                rejections.add("文本包含证据包之外的数字");
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private boolean containsHistoricalNumber(String value, Set<String> historicalNumbers) {
        Set<String> values = numbers(value);
        values.retainAll(historicalNumbers);
        return !values.isEmpty();
    }

    /**
     * 允许集合严格来自实际发送给模型的证据包字段。文本可以复述已有数字，不能自行计算或补值。
     */
    private Set<String> allowedNumbers(CapitalAgentEvidencePacket packet) {
        // 没有引用字段的摘要、假设和缺口文本不得复述历史统计；历史数字只在观察项局部门禁中开放。
        return nonHistoricalNumbers(packet);
    }

    private Set<String> observationNumbers(CapitalAgentEvidencePacket packet,
                                           List<String> factorRefs,
                                           List<String> metricRefs,
                                           List<String> evaluationRefs) {
        Set<String> result = new HashSet<String>();
        packet.getFactorObservations().stream()
                .filter(item -> factorRefs.contains(item.factorRef()))
                .forEach(item -> collectFactorNumbers(result, item));
        packet.getRawMetrics().stream()
                .filter(item -> metricRefs.contains(item.getRef()))
                .forEach(item -> collectMetricNumbers(result, item));
        packet.getHistoricalEvaluations().stream()
                .filter(item -> evaluationRefs.contains(item.evaluationRef()))
                .forEach(item -> {
                    collectNumbers(result, item.evaluationRef());
                    collectNumbers(result, item.getSignalType());
                    collectNumbers(result, item.getSignalLabel());
                    collectHistoricalNumbers(result, item);
                });
        return result;
    }

    private void collectFactorNumbers(Set<String> result, CapitalFactorObservation item) {
        collectNumbers(result, item.factorRef());
        collectNumbers(result, item.getFactorCode());
        collectNumbers(result, item.getLabel());
        collectNumbers(result, item.getWindow());
        collectNumbers(result, item.getValue());
        collectNumbers(result, item.getBaseline());
        collectNumbers(result, item.getPercentile());
        collectNumbers(result, item.getZScore());
        collectNumbers(result, item.getState());
        collectNumbers(result, item.getMetricRefs());
    }

    private void collectMetricNumbers(Set<String> result, CapitalEvidenceRef item) {
        collectNumbers(result, item.getRef());
        collectNumbers(result, item.getLabel());
        collectNumbers(result, item.getValue());
        collectNumbers(result, item.getUnit());
        collectNumbers(result, item.getObservedAt());
    }

    private Set<String> nonHistoricalNumbers(CapitalAgentEvidencePacket packet) {
        Set<String> result = new HashSet<String>();
        collectNumbers(result, packet.getSnapshotId());
        collectNumbers(result, packet.getAsOf());
        for (CapitalFactorObservation item : packet.getFactorObservations()) {
            collectNumbers(result, item.getFactorRef());
            collectNumbers(result, item.getFactorCode());
            collectNumbers(result, item.getLabel());
            collectNumbers(result, item.getWindow());
            collectNumbers(result, item.getValue());
            collectNumbers(result, item.getBaseline());
            collectNumbers(result, item.getPercentile());
            collectNumbers(result, item.getZScore());
            collectNumbers(result, item.getState());
            collectNumbers(result, item.getMetricRefs());
        }
        for (CapitalBehaviorSignal item : packet.getSignals()) {
            collectNumbers(result, item.getType());
            collectNumbers(result, item.getLabel());
            collectNumbers(result, item.getWindow());
            collectNumbers(result, item.getFactorRefs());
            collectNumbers(result, item.getMetricRefs());
            collectNumbers(result, item.getActualValues());
            collectNumbers(result, item.getThresholds());
        }
        for (CapitalEvidenceRef item : packet.getRawMetrics()) {
            collectNumbers(result, item.getRef());
            collectNumbers(result, item.getLabel());
            collectNumbers(result, item.getValue());
            collectNumbers(result, item.getUnit());
            collectNumbers(result, item.getObservedAt());
        }
        for (CapitalWatchCondition item : packet.getWatchConditions()) {
            collectNumbers(result, item.getId());
            collectNumbers(result, item.getLabel());
            collectNumbers(result, item.getFactorRef());
            collectNumbers(result, item.getThreshold());
            collectNumbers(result, item.getUnit());
        }
        collectNumbers(result, packet.getDataGaps());
        return result;
    }

    private Set<String> historicalNumbers(CapitalAgentEvidencePacket packet, Set<String> refs) {
        Set<String> result = new HashSet<String>();
        packet.getHistoricalEvaluations().stream()
                .filter(item -> refs == null || refs.contains(item.evaluationRef()))
                .forEach(item -> collectHistoricalNumbers(result, item));
        return result;
    }

    private void collectHistoricalNumbers(Set<String> destination, CapitalSignalEvaluation item) {
        collectNumbers(destination, item.getHorizonDays());
        collectNumbers(destination, item.getSampleCount());
        collectRatioNumbers(destination, item.getAverageReturn());
        collectRatioNumbers(destination, item.getMedianReturn());
        collectRatioNumbers(destination, item.getPositiveRate());
        collectRatioNumbers(destination, item.getAverageMfe());
        collectRatioNumbers(destination, item.getAverageMae());
        collectNumbers(destination, item.getLastEventDate());
    }

    private void collectRatioNumbers(Set<String> destination, java.math.BigDecimal value) {
        collectNumbers(destination, value);
        if (value != null) collectNumbers(destination, value.multiply(new java.math.BigDecimal("100")));
    }

    private Set<String> numbers(String value) {
        Set<String> result = new HashSet<String>();
        collectNumbers(result, value);
        return result;
    }

    private void collectNumbers(Set<String> destination, Object value) {
        if (value == null) return;
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) collectNumbers(destination, item);
            return;
        }
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                collectNumbers(destination, entry.getKey());
                collectNumbers(destination, entry.getValue());
            }
            return;
        }
        Matcher matcher = NUMBER.matcher(String.valueOf(value));
        while (matcher.find()) destination.add(normalizeNumber(matcher.group()));
    }

    private boolean containsExternalNumber(String value, Set<String> allowedNumbers) {
        return !externalNumbers(value, allowedNumbers).isEmpty();
    }

    private Set<String> externalNumbers(String value, Set<String> allowedNumbers) {
        Set<String> result = new LinkedHashSet<String>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String normalized = normalizeNumber(matcher.group());
            if (!allowedNumbers.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private String normalizeNumber(String value) {
        String numeric = value.endsWith("%") ? value.substring(0, value.length() - 1) : value;
        return new java.math.BigDecimal(numeric).stripTrailingZeros().toPlainString();
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

    private List<String> optionalStrings(JsonNode nodes) {
        return nodes == null || nodes.isNull() ? Collections.emptyList() : strings(nodes);
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
