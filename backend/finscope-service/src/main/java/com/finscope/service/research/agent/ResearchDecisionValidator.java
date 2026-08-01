package com.finscope.service.research.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.service.research.mission.ResearchQueryNormalizer;
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
    private static final Set<String> MATERIAL_ARGUMENTS = new HashSet<String>(Arrays.asList(
            "stockCode", "materialType", "query"));
    private static final Set<String> MATERIAL_TYPES = new HashSet<String>(Arrays.asList(
            "ANNOUNCEMENT", "INTERACTION", "BROKER_REPORT", "NEWS_FLASH"));
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
            validateToolCall(draft);
            validateMissionTask(draft, context);
            if (isExternalTool(draft.getToolCode()) && context.getRemainingActions() <= 0) {
                throw rejected("外部动作预算已用尽");
            }
            if (!empty(draft.getPlanPatch())) {
                throw rejected("TOOL_CALL 不得同时携带 planPatch");
            }
        } else if ("PLAN_PATCH".equals(type)) {
            if (hasText(draft.getToolCode()) || !empty(draft.getArguments())) {
                throw rejected("PLAN_PATCH 不得携带工具调用参数");
            }
            validatePlanPatch(draft.getPlanPatch());
        } else if (hasText(draft.getToolCode()) || hasText(draft.getMissionTaskKey())
                || !empty(draft.getArguments())) {
            throw rejected(type + " 不得携带工具或工具参数");
        }

        String argumentsJson = json("PLAN_PATCH".equals(type) ? draft.getPlanPatch() : draft.getArguments());
        String fingerprint = "TOOL_CALL".equals(type)
                ? fingerprint(draft.getToolCode(), draft.getMissionTaskKey(), argumentsJson, draft.getTargetGap())
                : null;
        if (fingerprint != null && context.getAttemptedFingerprints().contains(fingerprint)) {
            throw rejected("动作指纹已经执行，禁止重复调用");
        }

        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setResearchRunId(context.getResearchRunId());
        value.setIteration(context.getNextIteration());
        value.setDecisionType(type);
        value.setCurrentSubgoal(compact(draft.getCurrentSubgoal()));
        value.setMissionTaskKey(compactNullable(draft.getMissionTaskKey(), 48));
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
        if ("research_material_search".equals(tool)) {
            Map<String, Object> arguments = draft.getArguments();
            if (arguments == null || !MATERIAL_ARGUMENTS.equals(arguments.keySet())) {
                throw rejected("research_material_search 参数只能包含 stockCode、materialType 和 query");
            }
            String stockCode = text(arguments.get("stockCode"));
            if (stockCode == null || !stockCode.trim().matches("\\d{6}")) {
                throw rejected("stockCode 必须是六位 A 股代码");
            }
            String materialType = upper(text(arguments.get("materialType")));
            if (!MATERIAL_TYPES.contains(materialType)) {
                throw rejected("materialType 不在白名单中");
            }
            String query = text(arguments.get("query"));
            if (query == null || compact(query).length() > 100 || query.contains("://")) {
                throw rejected("research_material_search query 未通过安全校验");
            }
            arguments.put("stockCode", stockCode.trim());
            arguments.put("materialType", materialType);
            arguments.put("query", compact(query));
            requireText(draft.getExpectedObservation(), "expectedObservation", 320);
            return;
        }
        throw rejected("工具不在 Agent 可执行白名单中：" + tool);
    }

    private void validateMissionTask(ResearchDecisionDraft draft, ResearchDecisionContext context) {
        if (context.getTasks() == null || context.getTasks().isEmpty()) return;
        requireText(draft.getMissionTaskKey(), "missionTaskKey", 48);
        ResearchMissionTask selected = null;
        for (ResearchMissionTask task : context.getTasks()) {
            if (draft.getMissionTaskKey().trim().equals(task.getTaskKey())) {
                selected = task;
                break;
            }
        }
        if (selected == null) throw rejected("missionTaskKey 不属于当前计划");
        if (!("PENDING".equals(selected.getStatus()) || "FAILED".equals(selected.getStatus())
                || "INTERRUPTED".equals(selected.getStatus()))) {
            throw rejected("missionTaskKey 不是可执行任务");
        }
        if (!draft.getToolCode().trim().equals(selected.getToolCode())) {
            throw rejected("missionTaskKey 与工具不匹配");
        }
        for (String dependency : selected.getDependencies()) {
            ResearchMissionTask dependencyTask = task(context, dependency);
            if (dependencyTask == null || !("COMPLETED".equals(dependencyTask.getStatus())
                    || ("SKIPPED".equals(dependencyTask.getStatus())
                    && "SUFFICIENT_EVIDENCE".equals(dependencyTask.getSkipReason())))) {
                throw rejected("missionTaskKey 依赖尚未完成：" + dependency);
            }
        }
        Map<String, Object> arguments = draft.getArguments();
        if ("public_news_search".equals(selected.getToolCode())) {
            if (!selected.getIntent().equals(upper(text(arguments.get("intent"))))
                    || !ResearchQueryNormalizer.normalize(selected.getQueryText())
                    .equals(ResearchQueryNormalizer.normalize(text(arguments.get("query"))))) {
                throw rejected("公开搜索参数与 missionTaskKey 不匹配");
            }
        } else if ("research_material_search".equals(selected.getToolCode())) {
            String[] expected = selected.getQueryText() == null
                    ? new String[0] : selected.getQueryText().trim().split("\\s+", 3);
            String expectedQuery = expected.length == 3 ? ResearchQueryNormalizer.normalize(expected[2]) : "";
            if (expected.length < 2 || !expected[0].equals(text(arguments.get("stockCode")))
                    || !expected[1].equals(upper(text(arguments.get("materialType"))))
                    || !expectedQuery.equals(ResearchQueryNormalizer.normalize(text(arguments.get("query"))))) {
                throw rejected("结构化资料参数与 missionTaskKey 不匹配");
            }
        }
    }

    private ResearchMissionTask task(ResearchDecisionContext context, String taskKey) {
        for (ResearchMissionTask task : context.getTasks()) {
            if (taskKey.equals(task.getTaskKey())) return task;
        }
        return null;
    }

    private void validatePlanPatch(Map<String, Object> patch) {
        Set<String> fields = new HashSet<String>(Arrays.asList(
                "operation", "taskKey", "title", "question", "toolCode", "intent", "queryText", "reason"));
        if (patch == null || patch.size() != fields.size() || !fields.equals(patch.keySet())) {
            throw rejected("planPatch 字段不完整或包含未知字段");
        }
        if (!"ADD_OR_REPLACE_PENDING_TASK".equals(text(patch.get("operation")))) {
            throw rejected("planPatch operation 不受支持");
        }
        String taskKey = text(patch.get("taskKey"));
        if (taskKey == null || !taskKey.matches("adaptive_[a-z0-9_]{1,48}")) {
            throw rejected("planPatch taskKey 必须使用 adaptive_ 前缀");
        }
        if (!"public_news_search".equals(text(patch.get("toolCode")))) {
            throw rejected("planPatch 工具不在白名单中");
        }
        String intent = upper(text(patch.get("intent")));
        if (!SEARCH_INTENTS.contains(intent)) {
            throw rejected("planPatch intent 不在白名单中");
        }
        requireText(text(patch.get("title")), "planPatch.title", 100);
        requireText(text(patch.get("question")), "planPatch.question", 240);
        String query = text(patch.get("queryText"));
        requireText(query, "planPatch.queryText", 180);
        if (query.contains("://")) {
            throw rejected("planPatch.queryText 不能包含 URL");
        }
        requireText(text(patch.get("reason")), "planPatch.reason", 240);
        patch.put("intent", intent);
        patch.put("queryText", ResearchQueryNormalizer.normalize(query));
    }

    private String fingerprint(String toolCode, String missionTaskKey, String argumentsJson, String targetGap) {
        String canonical = toolCode.trim() + "|" + compactNullable(missionTaskKey, 48)
                + "|" + argumentsJson + "|" + compactNullable(targetGap, 320);
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
    private boolean isExternalTool(String toolCode) {
        return "public_news_search".equals(toolCode) || "research_material_search".equals(toolCode);
    }
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
