package com.finscope.service.research.mission;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.research.ModelJsonExtractor;
import com.finscope.service.research.ModelJsonShapeNormalizer;
import com.finscope.service.research.method.ResearchMethodDefinition;
import com.finscope.service.research.method.ResearchMethodRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ResearchPlanningAgent {
    static final int TIMEOUT_MS = 30_000;
    static final int MAX_OUTPUT_TOKENS = 2_000;
    private static final int MAX_RAW_CHARACTERS = 16_000;
    private static final Logger LOG = LoggerFactory.getLogger(ResearchPlanningAgent.class);

    private final LlmChatClient llmChatClient;
    private final ResearchPlanValidator validator;
    private final DeterministicResearchPlanner deterministicPlanner;
    private final ResearchMethodRegistry methodRegistry;
    private final ObjectMapper objectMapper;
    private final ModelJsonShapeNormalizer shapeNormalizer;

    public ResearchPlanningAgent(LlmChatClient llmChatClient,
                                 ResearchToolRegistry toolRegistry,
                                 ResearchPlanValidator validator,
                                 DeterministicResearchPlanner deterministicPlanner) {
        this(llmChatClient, toolRegistry, validator, deterministicPlanner, ResearchMethodRegistry.defaults());
    }

    @Autowired
    public ResearchPlanningAgent(LlmChatClient llmChatClient,
                                 ResearchToolRegistry toolRegistry,
                                 ResearchPlanValidator validator,
                                 DeterministicResearchPlanner deterministicPlanner,
                                 ResearchMethodRegistry methodRegistry) {
        this.llmChatClient = llmChatClient;
        this.validator = validator;
        this.deterministicPlanner = deterministicPlanner;
        this.methodRegistry = methodRegistry;
        this.objectMapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.shapeNormalizer = new ModelJsonShapeNormalizer(objectMapper);
    }

    public ResearchPlanningResult plan(ResearchPlanningInput input) {
        validateInput(input);
        ResearchMissionDraft controlled = controlledPlan(input);
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            return success(controlled, "CONTROLLED");
        }
        String raw;
        try {
            raw = call(systemPrompt(), userPrompt(input, controlled));
        } catch (Exception error) {
            return assistanceUnavailable(input, controlled, error);
        }
        Exception formatFailure;
        try {
            return success(enrich(input, raw), "MODEL_ASSISTED");
        } catch (Exception error) {
            formatFailure = error;
        }
        try {
            String repairedRaw = call(systemPrompt(), repairPrompt(input, controlled, formatFailure));
            return success(enrich(input, repairedRaw), "MODEL_ASSISTED");
        } catch (Exception repairFailure) {
            return assistanceUnavailable(input, controlled, repairFailure);
        }
    }

    private String call(String system, String user) throws Exception {
        return llmChatClient.complete(system, user, TIMEOUT_MS, MAX_OUTPUT_TOKENS);
    }

    private ResearchPlanningResult success(ResearchMissionDraft draft, String mode) {
        return new ResearchPlanningResult(draft, mode, null, null);
    }

    private ResearchPlanningResult assistanceUnavailable(ResearchPlanningInput input,
                                                          ResearchMissionDraft controlled,
                                                          Exception error) {
        String category = isTimeout(error) ? "TIMEOUT" : error.getClass().getSimpleName();
        LOG.warn("Research plan assistance unavailable; model={}, subject={}, category={}",
                llmChatClient.modelName(), compact(input.getSubjectName(), 80), category);
        return new ResearchPlanningResult(controlled, "CONTROLLED", "MODEL_ASSISTANCE_UNAVAILABLE",
                "模型辅助未采用（" + category + "），研究继续使用服务端受控计划");
    }

    private ResearchMissionDraft controlledPlan(ResearchPlanningInput input) {
        return validator.validate(deterministicPlanner.plan(input), input);
    }

    private ResearchMissionDraft enrich(ResearchPlanningInput input, String raw) throws Exception {
        JsonNode parsed = objectMapper.readTree(ModelJsonExtractor.extractObject(raw, MAX_RAW_CHARACTERS));
        if (!(parsed instanceof ObjectNode)) {
            throw new BusinessException(BizErrorCode.RESEARCH_PLAN_ENRICH_NOT_OBJECT);
        }
        ObjectNode root = (ObjectNode) parsed;
        shapeNormalizer.normalizeStringArrayField(root, "successCriteria");
        shapeNormalizer.normalizeObjectArrayField(root, "taskRefinements");

        ResearchMissionDraft draft = deterministicPlanner.plan(input);
        boolean applied = false;
        String scopeSummary = optionalText(root, "scopeSummary");
        if (hasText(scopeSummary)) {
            draft.setScopeSummary(scopeSummary);
            applied = true;
        }
        JsonNode criteria = root.get("successCriteria");
        if (criteria != null && criteria.isArray() && criteria.size() > 0) {
            java.util.List<String> values = new java.util.ArrayList<String>();
            for (JsonNode item : criteria) {
                if (hasText(item.asText())) values.add(item.asText().trim());
            }
            if (!values.isEmpty()) {
                draft.setSuccessCriteria(values);
                applied = true;
            }
        }
        JsonNode refinements = root.get("taskRefinements");
        if (refinements != null && refinements.isArray()) {
            for (JsonNode item : refinements) {
                if (!(item instanceof ObjectNode)) continue;
                applied |= applyRefinement(draft, (ObjectNode) item);
            }
        }
        if (!applied) {
            throw new BusinessException(BizErrorCode.RESEARCH_PLAN_ENRICH_EMPTY);
        }
        return validator.validate(draft, input);
    }

    private boolean applyRefinement(ResearchMissionDraft draft, ObjectNode refinement) throws Exception {
        String taskKey = optionalText(refinement, "taskKey");
        if (!hasText(taskKey)) return false;
        ResearchMissionTaskDraft task;
        try {
            task = draft.task(taskKey.trim());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        boolean applied = false;
        String query = optionalText(refinement, "queryText");
        if (hasText(query) && "SEARCH".equals(task.getTaskType())) {
            task.setQueryText(refinedQuery(task, query));
            applied = true;
        }
        String rationale = optionalText(refinement, "rationale");
        if (hasText(rationale)) {
            task.setRationale(rationale);
            applied = true;
        }
        String expected = optionalText(refinement, "expectedEvidence");
        if (hasText(expected)) {
            task.setExpectedEvidence(expected);
            applied = true;
        }
        return applied;
    }

    private String refinedQuery(ResearchMissionTaskDraft task, String suggested) {
        if (!"research_material_search".equals(task.getToolCode())) return suggested;
        String[] contract = task.getQueryText() == null
                ? new String[0] : task.getQueryText().trim().split("\\s+", 3);
        if (contract.length < 2) return task.getQueryText();
        String naturalQuery = suggested.trim().replaceFirst(
                "(?i)^\\d{6}\\s+(ANNOUNCEMENT|INTERACTION|BROKER_REPORT|NEWS_FLASH)\\s*", "");
        return contract[0] + " " + contract[1] + (naturalQuery.isEmpty() ? "" : " " + naturalQuery);
    }

    private String systemPrompt() {
        return "你是 FinScope 的研究计划增强器。任务图、工具合同、依赖、方法和预算均由服务端确定，"
                + "你只能优化研究范围、成功条件和已有任务的检索表述。只返回单个JSON对象，不要Markdown。"
                + "字段仅使用scopeSummary、successCriteria、taskRefinements；"
                + "taskRefinements元素仅使用taskKey、queryText、rationale、expectedEvidence。"
                + "不得新增任务，不得输出工具、依赖、methodCodes、URL、SQL、Shell或文件路径。";
    }

    private String userPrompt(ResearchPlanningInput input, ResearchMissionDraft controlled) {
        StringBuilder value = new StringBuilder();
        value.append("当前日期：").append(input.getCurrentDate()).append('\n')
                .append("研究问题：").append(compact(input.getQuestion(), 180)).append('\n')
                .append("对象类型：").append(compact(input.getSubjectType(), 40)).append('\n')
                .append("研究对象：").append(compact(input.getSubjectName(), 100)).append('\n')
                .append("对象代码：").append(compact(input.getSubjectCode(), 20)).append('\n')
                .append("服务端已选方法：").append(controlled.getMethodCodes()).append('\n')
                .append("必需证据：").append(controlled.getRequiredEvidence()).append('\n')
                .append("反证检查：").append(controlled.getCounterChecks()).append("\n可增强任务：\n");
        for (ResearchMissionTaskDraft task : controlled.getTasks()) {
            if (!"SEARCH".equals(task.getTaskType())) continue;
            value.append("- ").append(task.getTaskKey()).append("；intent=").append(task.getIntent())
                    .append("；query=").append(compact(task.getQueryText(), 180)).append('\n');
        }
        List<ResearchMethodDefinition> methods = methodRegistry.recommend(input);
        for (ResearchMethodDefinition method : methods) {
            value.append("方法完成条件[").append(method.getCode()).append("]：")
                    .append(method.getCompletionCriteria()).append('\n');
        }
        value.append("请只增强上述服务端计划，不要重建计划。");
        return value.toString();
    }

    private String repairPrompt(ResearchPlanningInput input,
                                ResearchMissionDraft controlled,
                                Exception failure) {
        return userPrompt(input, controlled)
                + "\n上一次增强输出无效（" + failure.getClass().getSimpleName() + "）。"
                + "请重新返回一个完整、简短的JSON对象。";
    }

    private void validateInput(ResearchPlanningInput input) {
        if (input == null || !hasText(input.getQuestion()) || !hasText(input.getSubjectName())) {
            throw new IllegalArgumentException("研究命题和研究对象不能为空");
        }
        if (input.getMaxActions() < 1 || input.getMaxActions() > 100) {
            throw new IllegalArgumentException("研究动作预算不合法");
        }
        if (input.getCurrentDate() == null) input.setCurrentDate(LocalDate.now());
    }

    private String optionalText(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw new IllegalArgumentException(field + " 必须是字符串");
        return node.asText();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String compact(String value, int maxLength) {
        if (value == null) return "";
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
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
