package com.finscope.service.attribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.common.enums.attribution.NewsImpactDirection;
import com.finscope.common.enums.attribution.NewsInterpretationType;
import com.finscope.domain.attribution.AttributionAnalysisPoint;
import com.finscope.domain.attribution.AttributionNewsAnalysis;

/** 容错读取可选深读字段；局部输出异常不影响原报告。 */
final class AttributionNewsAnalysisParser {
    private AttributionNewsAnalysisParser() {
    }

    static AttributionNewsAnalysis parse(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        AttributionNewsAnalysis result = new AttributionNewsAnalysis();
        if (node.path("types").isArray()) {
            for (JsonNode type : node.path("types")) {
                NewsInterpretationType value = enumValue(NewsInterpretationType.class, type);
                if (value != null && !result.getTypes().contains(value)) {
                    result.getTypes().add(value);
                }
                if (result.getTypes().size() >= 3) {
                    break;
                }
            }
        }
        result.setDirection(enumValue(NewsImpactDirection.class, node.path("direction")));
        result.setKeyChange(text(node.path("keyChange"), 300));
        if (node.path("businessImpacts").isArray()) {
            for (JsonNode item : node.path("businessImpacts")) {
                String label = text(item.path("label"), 40);
                String analysis = text(item.path("analysis"), 600);
                if (!label.isEmpty() && !analysis.isEmpty()) {
                    AttributionAnalysisPoint point = new AttributionAnalysisPoint();
                    point.setLabel(label);
                    point.setAnalysis(analysis);
                    result.getBusinessImpacts().add(point);
                }
                if (result.getBusinessImpacts().size() >= 4) {
                    break;
                }
            }
        }
        result.setShortTermImpact(text(node.path("shortTermImpact"), 400));
        result.setMediumTermImpact(text(node.path("mediumTermImpact"), 400));
        result.setLongTermImpact(text(node.path("longTermImpact"), 400));
        result.setChainReaction(text(node.path("chainReaction"), 500));
        if (result.getKeyChange().isEmpty() && result.getBusinessImpacts().isEmpty()
                && result.getShortTermImpact().isEmpty() && result.getMediumTermImpact().isEmpty()
                && result.getLongTermImpact().isEmpty() && result.getChainReaction().isEmpty()) {
            return null;
        }
        return result;
    }

    static String text(JsonNode node, int limit) {
        if (!node.isTextual()) {
            return "";
        }
        String value = node.asText().trim();
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, JsonNode node) {
        String value = text(node, 50);
        for (T candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return null;
    }
}
