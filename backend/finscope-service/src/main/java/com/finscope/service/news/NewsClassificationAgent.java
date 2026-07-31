package com.finscope.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.news.NewsCategory;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NewsClassificationAgent {
    private static final int CONTENT_LIMIT = 600;
    private final LlmChatClient llm;
    private final ObjectMapper json;

    public NewsClassificationAgent(LlmChatClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    public Map<String, Decision> classify(List<NewsClassificationCandidate> candidates,
                                          List<NewsCategory> categories) throws Exception {
        if (llm == null || !llm.isConfigured()) {
            throw new IllegalStateException("资讯分类 Agent 尚未配置");
        }
        String raw = llm.complete(systemPrompt(categories), userPrompt(candidates));
        JsonNode root = json.readTree(extractJsonArray(raw));
        Set<String> itemIds = new HashSet<String>();
        for (NewsClassificationCandidate value : candidates) itemIds.add(value.getItemId());
        Set<String> categoryCodes = new HashSet<String>();
        for (NewsCategory value : categories) categoryCodes.add(value.getCode());

        Map<String, Decision> decisions = new LinkedHashMap<String, Decision>();
        if (!root.isArray()) return decisions;
        for (JsonNode node : root) {
            String itemId = text(node, "itemId");
            String categoryCode = text(node, "categoryCode");
            double confidence = node.path("confidence").asDouble(-1.0);
            String reason = text(node, "reason");
            if (!itemIds.contains(itemId) || !categoryCodes.contains(categoryCode)
                    || confidence < 0.0 || confidence > 1.0 || reason.isEmpty()) continue;
            decisions.put(itemId, new Decision(itemId, categoryCode, confidence, reason));
        }
        return decisions;
    }

    public String modelName() {
        String value = llm == null ? null : llm.modelName();
        return value == null || value.trim().isEmpty() ? "llm" : value;
    }

    private String systemPrompt(List<NewsCategory> categories) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是金融资讯分类 Agent。只能从给定分类目录选择一个一级分类，不得创造新分类。")
                .append("输出纯 JSON 数组，每项包含 itemId、categoryCode、confidence(0到1)、reason。\n分类目录：\n");
        for (NewsCategory category : categories) {
            prompt.append("- ").append(category.getCode()).append(" | ").append(category.getName())
                    .append(" | ").append(category.getClassificationGuidance()).append('\n');
        }
        return prompt.toString();
    }

    private String userPrompt(List<NewsClassificationCandidate> candidates) throws Exception {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (NewsClassificationCandidate candidate : candidates) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("itemId", candidate.getItemId());
            value.put("title", candidate.getTitle());
            value.put("content", limit(candidate.getContent()));
            value.put("sourceName", candidate.getSourceName());
            value.put("publishedAt", candidate.getPublishedAt() == null ? null : candidate.getPublishedAt().toString());
            values.add(value);
        }
        return json.writeValueAsString(values);
    }

    private String extractJsonArray(String raw) {
        if (raw == null) return "[]";
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String limit(String value) {
        if (value == null) return "";
        return value.length() <= CONTENT_LIMIT ? value : value.substring(0, CONTENT_LIMIT);
    }

    public static final class Decision {
        private final String itemId;
        private final String categoryCode;
        private final double confidence;
        private final String reason;

        public Decision(String itemId, String categoryCode, double confidence, String reason) {
            this.itemId = itemId;
            this.categoryCode = categoryCode;
            this.confidence = confidence;
            this.reason = reason;
        }

        public String getItemId() { return itemId; }
        public String getCategoryCode() { return categoryCode; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
    }
}
