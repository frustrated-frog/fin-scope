package com.finscope.service.research.mission;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.research.ModelJsonShapeNormalizer;
import com.finscope.service.research.method.ResearchMethodDefinition;
import com.finscope.service.research.method.ResearchMethodRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ResearchPlanningAgent {
    static final int TIMEOUT_MS = 30_000;
    static final int MAX_OUTPUT_TOKENS = 2_000;
    private static final int MAX_RAW_CHARACTERS = 24_000;

    private final LlmChatClient llmChatClient;
    private final ResearchToolRegistry toolRegistry;
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
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.deterministicPlanner = deterministicPlanner;
        this.methodRegistry = methodRegistry;
        this.objectMapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.shapeNormalizer = new ModelJsonShapeNormalizer(objectMapper);
    }

    public ResearchPlanningResult plan(ResearchPlanningInput input) {
        validateInput(input);
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            return fallback(input, "MODEL_DISABLED", null);
        }
        try {
            String raw = llmChatClient.complete(systemPrompt(), userPrompt(input), TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            String json = stripFence(raw);
            if (json.length() > MAX_RAW_CHARACTERS) {
                throw new IllegalArgumentException("研究计划校验失败：模型输出超过字符上限");
            }
            JsonNode root = objectMapper.readTree(json);
            normalizeDraft(root);
            ResearchMissionDraft draft = objectMapper.treeToValue(root, ResearchMissionDraft.class);
            return new ResearchPlanningResult(validator.validate(draft, input), "LLM_VALIDATED", null, null);
        } catch (IllegalArgumentException exception) {
            return fallback(input, "PLAN_REJECTED", safeDetail(exception.getMessage()));
        } catch (Exception exception) {
            if (isTimeout(exception)) {
                return fallback(input, "MODEL_TIMEOUT", "模型规划响应超时，已使用规则计划");
            }
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getClass().getSimpleName() + "：" + exception.getMessage();
            return fallback(input, "PLAN_REJECTED", safeDetail(detail));
        }
    }

    private ResearchPlanningResult fallback(ResearchPlanningInput input, String reason, String detail) {
        ResearchMissionDraft fallback = validator.validate(deterministicPlanner.plan(input), input);
        return new ResearchPlanningResult(fallback, "DETERMINISTIC", reason, detail);
    }

    private void normalizeDraft(JsonNode root) throws Exception {
        if (!(root instanceof ObjectNode)) {
            return;
        }
        ObjectNode draft = (ObjectNode) root;
        shapeNormalizer.normalizeTextFields(draft, "researchType", "scopeSummary");
        shapeNormalizer.normalizeStringArrayFields(draft, "methodCodes", "requiredEvidence",
                "requiredCalculations", "counterChecks", "completionCriteria", "successCriteria");
        shapeNormalizer.normalizeObjectArrayField(draft, "tasks");
        JsonNode tasks = draft.get("tasks");
        if (tasks == null || !tasks.isArray()) {
            return;
        }
        for (JsonNode task : tasks) {
            if (!(task instanceof ObjectNode)) {
                continue;
            }
            ObjectNode taskDraft = (ObjectNode) task;
            shapeNormalizer.normalizeTextFields(taskDraft, "taskKey", "title", "question", "taskType",
                    "toolCode", "intent", "parallelGroup", "queryText", "rationale", "expectedEvidence");
            shapeNormalizer.normalizeStringArrayField(taskDraft, "dependencies");
        }
    }

    private String systemPrompt() {
        return "你是 FinScope 的受控研究规划器。只负责把研究命题拆成有限任务图，不执行工具。"
                + "只能使用用户消息中列出的工具编码，不得输出HTTP地址、SQL、Shell、文件路径或额外字段。"
                + "必须返回单个JSON对象，不要Markdown。任务不超过8个，公开搜索不超过4个，依赖必须无环。"
                + "methodCodes只能使用当前研究对象适配的可用投研方法中的编码；不得创造方法。"
                + "若可用投研方法为空，methodCodes必须输出[]。"
                + "必须输出researchType、methodCodes、requiredEvidence、requiredCalculations、counterChecks、completionCriteria；"
                + "除methodCodes外的方法蓝图字段由服务端按注册表重新生成，模型不得降低要求。"
                + "必须包含BASELINE、SUPPORT、COUNTER、ASSESS、SYNTHESIS意图。"
                + "taskKey必须匹配[a-z][a-z0-9_]{2,47}，例如baseline_scan；"
                + "dependencies只能精确引用同一计划中的taskKey。"
                + "scopeSummary必须是JSON字符串；successCriteria必须是JSON字符串数组，包含1到5项；"
                + "tasks必须是JSON对象数组，包含4到8项；dependencies必须是JSON字符串数组，没有依赖时输出[]；"
                + "其余任务字段都必须是JSON字符串，不适用的parallelGroup、queryText、rationale或expectedEvidence输出空字符串。"
                + "taskType只能是COLLECT、SEARCH、ASSESS、SYNTHESIS；"
                + "intent只能是BASELINE、SUPPORT、COUNTER、PRIMARY、BREADTH、ASSESS、SYNTHESIS。"
                + "source_scan只能搭配COLLECT和BASELINE；"
                + "public_news_search只能搭配SEARCH以及SUPPORT、COUNTER、PRIMARY或BREADTH；"
                + "research_material_search只能搭配SEARCH以及SUPPORT、COUNTER、PRIMARY或BREADTH，"
                + "queryText格式严格为‘六位代码 空格 ANNOUNCEMENT|INTERACTION|BROKER_REPORT|NEWS_FLASH 空格 自然语言关键词’；"
                + "对于包含六位A股代码的对象，按研究地图、公司一手披露、专业资料、支持证据、反方证据、证据评估、报告合成组织任务；"
                + "优先一手材料，再用专业与新闻语境交叉核对，并主动寻找替代解释和证据缺口；"
                + "evidence_assess只能搭配ASSESS和ASSESS；"
                + "report_synthesis只能搭配SYNTHESIS和SYNTHESIS。"
                + "JSON字段严格为researchType、methodCodes、requiredEvidence、requiredCalculations、counterChecks、"
                + "completionCriteria、scopeSummary、successCriteria、tasks；任务字段严格为taskKey、title、question、"
                + "taskType、toolCode、intent、dependencies、parallelGroup、queryText、rationale、expectedEvidence。";
    }

    private String userPrompt(ResearchPlanningInput input) {
        StringBuilder value = new StringBuilder();
        value.append("当前日期：").append(input.getCurrentDate()).append('\n')
                .append("研究问题：").append(compact(input.getQuestion(), 180)).append('\n')
                .append("对象类型：").append(compact(input.getSubjectType(), 40)).append('\n')
                .append("研究对象：").append(compact(input.getSubjectName(), 100)).append('\n')
                .append("对象代码：").append(compact(input.getSubjectCode(), 20)).append('\n')
                .append("主题代码：").append(input.getThemeCodes()).append('\n')
                .append("最大外部动作预算：").append(input.getMaxActions()).append("\n可用工具：\n");
        List<ResearchToolDescriptor> tools = toolRegistry.list();
        for (ResearchToolDescriptor tool : tools) {
            value.append("- ").append(tool.getCode()).append("：")
                    .append(tool.getName()).append("；").append(tool.getDescription())
                    .append("；预算=").append(tool.getBudgetType()).append('\n');
        }
        List<ResearchMethodDefinition> methods = methodRegistry.recommend(input);
        if (methods.isEmpty()) {
            value.append("可用投研方法：无（当前研究对象没有适配的注册方法）\n");
        } else {
            value.append("可用投研方法：\n");
        }
        for (ResearchMethodDefinition method : methods) {
            value.append("- ").append(method.getCode()).append("：").append(method.getName())
                    .append("；").append(method.getDescription())
                    .append("；必查问题=").append(method.getRequiredQuestions())
                    .append("；必需证据=").append(method.getRequiredEvidence())
                    .append("；确定性计算=").append(method.getRequiredCalculations())
                    .append("；反证检查=").append(method.getCounterChecks())
                    .append("；完成条件=").append(method.getCompletionCriteria())
                    .append("；必需意图=").append(method.getRequiredIntents()).append('\n');
        }
        value.append("请生成完整研究合同和任务图。搜索queryText只写自然语言关键词，不得包含协议头。");
        return value.toString();
    }

    private void validateInput(ResearchPlanningInput input) {
        if (input == null || blank(input.getQuestion()) || blank(input.getSubjectName())) {
            throw new IllegalArgumentException("研究命题和研究对象不能为空");
        }
        if (input.getMaxActions() < 1 || input.getMaxActions() > 100) {
            throw new IllegalArgumentException("研究动作预算不合法");
        }
        if (input.getCurrentDate() == null) {
            input.setCurrentDate(LocalDate.now());
        }
    }

    private String stripFence(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("```json")) {
            value = value.substring(7);
        } else if (value.startsWith("```")) {
            value = value.substring(3);
        }
        if (value.endsWith("```")) {
            value = value.substring(0, value.length() - 3);
        }
        return value.trim();
    }

    private String safeDetail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "模型计划无法解析或未通过校验";
        }
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compacted.length() <= 320 ? compacted : compacted.substring(0, 320);
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
