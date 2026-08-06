package com.finscope.service.research.agent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.research.ModelJsonExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResearchDecisionAgent {
    static final int TIMEOUT_MS = 20_000;
    static final int MAX_OUTPUT_TOKENS = 1_200;
    private static final int MAX_RAW_CHARACTERS = 8_000;
    private static final Logger LOG = LoggerFactory.getLogger(ResearchDecisionAgent.class);

    private final LlmChatClient llmChatClient;
    private final DeterministicResearchPolicy controlPolicy;
    private final ObjectMapper objectMapper;

    public ResearchDecisionAgent(LlmChatClient llmChatClient,
                                 ResearchDecisionValidator validator,
                                 DeterministicResearchPolicy controlPolicy) {
        this.llmChatClient = llmChatClient;
        this.controlPolicy = controlPolicy;
        this.objectMapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public ResearchDecisionResult decide(ResearchDecisionContext context) {
        ResearchAgentDecision controlled = controlled(context);
        if (!canUseModel(context, controlled)) {
            return success(controlled);
        }
        List<ResearchMissionTask> candidates = eligibleTasks(context);
        if (candidates.isEmpty()) {
            return success(controlled);
        }

        String raw;
        try {
            raw = call(systemPrompt(candidates), selectionPrompt(context, candidates));
        } catch (Exception error) {
            return assistanceUnavailable(context, controlled, error);
        }
        Exception formatFailure;
        try {
            return success(modelSelection(context, candidates, raw));
        } catch (Exception error) {
            formatFailure = error;
        }
        try {
            String prompt = selectionPrompt(context, candidates)
                    + "\n上一次输出无效（" + formatFailure.getClass().getSimpleName() + "）。"
                    + "请只返回完整JSON对象。";
            return success(modelSelection(context, candidates, call(systemPrompt(candidates), prompt)));
        } catch (Exception repairFailure) {
            return assistanceUnavailable(context, controlled, repairFailure);
        }
    }

    private String call(String system, String user) throws Exception {
        return llmChatClient.complete(system, user, TIMEOUT_MS, MAX_OUTPUT_TOKENS);
    }

    private ResearchDecisionResult success(ResearchAgentDecision decision) {
        return new ResearchDecisionResult(decision, null, null);
    }

    private ResearchDecisionResult assistanceUnavailable(ResearchDecisionContext context,
                                                          ResearchAgentDecision controlled,
                                                          Exception error) {
        String category = isTimeout(error) ? "TIMEOUT" : error.getClass().getSimpleName();
        LOG.warn("Research decision assistance unavailable; model={}, runId={}, category={}",
                llmChatClient.modelName(), context == null ? null : context.getResearchRunId(), category);
        return new ResearchDecisionResult(controlled, "MODEL_ASSISTANCE_UNAVAILABLE",
                "模型辅助未采用（" + category + "），本轮继续使用服务端受控决策");
    }

    private ResearchAgentDecision controlled(ResearchDecisionContext context) {
        ResearchAgentDecision decision = controlPolicy.decide(context);
        decision.setDecisionMode("CONTROLLED");
        return decision;
    }

    private boolean canUseModel(ResearchDecisionContext context, ResearchAgentDecision controlled) {
        return controlled != null && "TOOL_CALL".equals(controlled.getDecisionType())
                && isExternalTool(controlled.getToolCode())
                && context != null && context.getRemainingActions() > 0
                && context.getTasks() != null && !context.getTasks().isEmpty()
                && llmChatClient != null && llmChatClient.isConfigured();
    }

    private List<ResearchMissionTask> eligibleTasks(ResearchDecisionContext context) {
        List<ResearchMissionTask> values = new ArrayList<ResearchMissionTask>();
        for (ResearchMissionTask task : context.getTasks()) {
            if (!isExternalTool(task.getToolCode())) continue;
            try {
                controlPolicy.decideTask(context, task.getTaskKey(), "MODEL_ASSISTED");
                values.add(task);
            } catch (IllegalArgumentException ignored) {
                // Not ready, already attempted, or outside the active task contract.
            }
        }
        return values;
    }

    private ResearchAgentDecision modelSelection(ResearchDecisionContext context,
                                                 List<ResearchMissionTask> candidates,
                                                 String raw) throws Exception {
        JsonNode root = objectMapper.readTree(ModelJsonExtractor.extractObject(raw, MAX_RAW_CHARACTERS));
        String taskKey = text(root.get("missionTaskKey"));
        if (!hasText(taskKey) || !containsTask(candidates, taskKey.trim())) {
            throw new BusinessException(BizErrorCode.RESEARCH_DECISION_TASK_KEY_INVALID);
        }
        ResearchAgentDecision decision = controlPolicy.decideTask(context, taskKey.trim(), "MODEL_ASSISTED");
        String summary = compact(text(root.get("decisionSummary")), 480);
        if (hasText(summary)) decision.setDecisionSummary(summary);
        decision.setConfidence(confidence(root.get("confidence"), decision.getConfidence()));
        return decision;
    }

    private String systemPrompt(List<ResearchMissionTask> candidates) {
        StringBuilder keys = new StringBuilder();
        for (ResearchMissionTask task : candidates) {
            if (keys.length() > 0) keys.append(',');
            keys.append(task.getTaskKey());
        }
        return "你是 FinScope 的任务选择助手。工具、参数、缺口和任务合同均由服务端生成。"
                + "你只能从候选任务键中选择一个。只返回单个JSON对象，不要Markdown。"
                + "字段仅使用missionTaskKey、decisionSummary、confidence；confidence为0到1数字。"
                + "候选任务键：" + keys + "。不得输出工具参数或计划补丁。";
    }

    private String selectionPrompt(ResearchDecisionContext context, List<ResearchMissionTask> candidates) {
        StringBuilder prompt = new StringBuilder();
        if (context.getMission() != null) {
            prompt.append("研究目标：").append(compact(context.getMission().getGoal(), 240)).append('\n')
                    .append("研究对象：").append(compact(context.getMission().getSubject(), 120)).append('\n');
        }
        ResearchMissionGap gap = context.getLatestGap();
        if (gap != null) {
            prompt.append("证据缺口：evidence=").append(gap.getEvidenceCount())
                    .append(",sources=").append(gap.getSourceCount())
                    .append(",support=").append(gap.getSupportCount())
                    .append(",counter=").append(gap.getCounterCount())
                    .append(",recommendedIntent=").append(gap.getRecommendedIntent()).append('\n');
        }
        prompt.append("候选任务：\n");
        for (ResearchMissionTask task : candidates) {
            prompt.append("- ").append(task.getTaskKey()).append("；intent=").append(task.getIntent())
                    .append("；title=").append(compact(task.getTitle(), 100))
                    .append("；reason=").append(compact(task.getRationale(), 180)).append('\n');
        }
        prompt.append("选择最能缩小当前证据缺口的一个任务。");
        return prompt.toString();
    }

    private boolean containsTask(List<ResearchMissionTask> tasks, String key) {
        for (ResearchMissionTask task : tasks) {
            if (key.equals(task.getTaskKey())) return true;
        }
        return false;
    }

    private double confidence(JsonNode node, double fallback) {
        if (node == null || node.isNull()) return fallback;
        if (node.isNumber()) return bounded(node.asDouble(), fallback);
        String value = node.asText().trim();
        if ("HIGH".equalsIgnoreCase(value)) return 0.85D;
        if ("MEDIUM".equalsIgnoreCase(value) || "MID".equalsIgnoreCase(value)) return 0.65D;
        if ("LOW".equalsIgnoreCase(value)) return 0.35D;
        try {
            return bounded(Double.parseDouble(value), fallback);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double bounded(double value, double fallback) {
        return value >= 0D && value <= 1D ? value : fallback;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String compact(String value, int maxLength) {
        if (value == null) return "";
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
    }

    private boolean isExternalTool(String toolCode) {
        return "public_news_search".equals(toolCode) || "research_material_search".equals(toolCode);
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }
}
