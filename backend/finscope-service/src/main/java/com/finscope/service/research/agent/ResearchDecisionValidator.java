package com.finscope.service.research.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ResearchDecisionValidator {
    private static final Set<String> DECISION_TYPES = new HashSet<String>(Arrays.asList(
            "TOOL_CALL", "PLAN_PATCH", "FINISH", "ABORT"));
    private static final Set<String> SEARCH_INTENTS = new HashSet<String>(Arrays.asList(
            "SUPPORT", "COUNTER", "PRIMARY", "UPDATE"));
    private static final Set<String> SEARCH_ARGUMENTS = new HashSet<String>(Arrays.asList("query", "intent"));
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public ResearchAgentDecision validate(ResearchDecisionDraft draft,
                                          ResearchDecisionContext context,
                                          String decisionMode) {
        if (draft == null || context == null || context.getResearchRunId() == null) {
            throw rejected("决策和运行上下文不能为空");
        }
        String type = upper(draft.getDecisionType());
        if (!DECISION_TYPES.contains(type)) {
            throw rejected("decisionType 不在白名单中");
        }
        requireText(draft.getCurrentSubgoal(), "currentSubgoal", 240);
        requireText(draft.getDecisionSummary(), "decisionSummary", 480);
        if (draft.getConfidence() < 0D || draft.getConfidence() > 1D) {
            throw rejected("confidence 必须位于 0 到 1");
        }
        if ("TOOL_CALL".equals(type)) {
            if (context.getRemainingActions() <= 0) {
                throw rejected("外部动作预算已用尽");
            }
            validateToolCall(draft);
        } else if (hasText(draft.getToolCode()) || !empty(draft.getArguments())) {
            throw rejected(type + " 不得携带工具或工具参数");
        }

        String argumentsJson = json(draft.getArguments());
        String fingerprint = "TOOL_CALL".equals(type)
                ? fingerprint(draft.getToolCode(), argumentsJson, draft.getTargetGap())
                : null;
        if (fingerprint != null && context.getAttemptedFingerprints().contains(fingerprint)) {
            throw rejected("动作指纹已经执行，禁止重复调用");
        }

        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setResearchRunId(context.getResearchRunId());
        value.setIteration(context.getNextIteration());
        value.setDecisionType(type);
        value.setCurrentSubgoal(compact(draft.getCurrentSubgoal()));
        value.setToolCode(hasText(draft.getToolCode()) ? draft.getToolCode().trim() : null);
        value.setArgumentsJson(argumentsJson);
        value.setTargetGap(compactNullable(draft.getTargetGap(), 320));
        value.setExpectedObservation(compactNullable(draft.getExpectedObservation(), 320));
        value.setDecisionSummary(compact(draft.getDecisionSummary()));
        value.setConfidence(draft.getConfidence());
        value.setDecisionMode(hasText(decisionMode) ? decisionMode.trim() : "MODEL");
        value.setActionFingerprint(fingerprint);
        value.setStatus("PROPOSED");
        return value;
    }

    private void validateToolCall(ResearchDecisionDraft draft) {
        String tool = draft.getToolCode() == null ? "" : draft.getToolCode().trim();
        if ("public_news_search".equals(tool)) {
            Map<String, Object> arguments = draft.getArguments();
            if (!SEARCH_ARGUMENTS.containsAll(arguments.keySet()) || arguments.size() != 2) {
                throw rejected("public_news_search 参数只能包含 query 和 intent");
            }
            String query = text(arguments.get("query"));
            requireText(query, "query", 240);
            if (query.contains("://")) {
                throw rejected("query 只能是自然语言关键词，不能包含 URL");
            }
            String intent = upper(text(arguments.get("intent")));
            if (!SEARCH_INTENTS.contains(intent)) {
                throw rejected("intent 不在白名单中");
            }
            arguments.put("query", compact(query));
            arguments.put("intent", intent);
            requireText(draft.getExpectedObservation(), "expectedObservation", 320);
            return;
        }
        if ("evidence_assess".equals(tool)) {
            if (!empty(draft.getArguments())) {
                throw rejected("evidence_assess 不接受参数");
            }
            return;
        }
        throw rejected("工具不在 Agent 可执行白名单中：" + tool);
    }

    private String fingerprint(String toolCode, String argumentsJson, String targetGap) {
        String canonical = toolCode.trim() + "|" + argumentsJson + "|" + compactNullable(targetGap, 320);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return toolCode.trim() + ":" + hex.substring(0, 24);
        } catch (Exception impossible) {
            throw new IllegalStateException("JDK does not provide SHA-256", impossible);
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Collections.emptyMap() : value);
        } catch (Exception error) {
            throw rejected("工具参数无法序列化");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (!hasText(value)) {
            throw rejected(field + " 不能为空");
        }
        if (compact(value).length() > maxLength) {
            throw rejected(field + " 超过长度上限 " + maxLength);
        }
    }

    private boolean empty(Map<String, Object> value) { return value == null || value.isEmpty(); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String compact(String value) { return value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim(); }
    private String compactNullable(String value, int maxLength) {
        if (!hasText(value)) return null;
        String compacted = compact(value);
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
    }
    private IllegalArgumentException rejected(String reason) {
        return new IllegalArgumentException("研究决策校验失败：" + reason);
    }
}
