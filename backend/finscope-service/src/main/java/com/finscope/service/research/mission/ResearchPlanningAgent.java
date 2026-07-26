package com.finscope.service.research.mission;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ResearchPlanningAgent {
    static final int TIMEOUT_MS = 8_000;
    static final int MAX_OUTPUT_TOKENS = 2_000;
    private static final int MAX_RAW_CHARACTERS = 24_000;

    private final LlmChatClient llmChatClient;
    private final ResearchToolRegistry toolRegistry;
    private final ResearchPlanValidator validator;
    private final DeterministicResearchPlanner deterministicPlanner;
    private final ObjectMapper objectMapper;

    public ResearchPlanningAgent(LlmChatClient llmChatClient,
                                 ResearchToolRegistry toolRegistry,
                                 ResearchPlanValidator validator,
                                 DeterministicResearchPlanner deterministicPlanner) {
        this.llmChatClient = llmChatClient;
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.deterministicPlanner = deterministicPlanner;
        this.objectMapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
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
            ResearchMissionDraft draft = objectMapper.readValue(json, ResearchMissionDraft.class);
            return new ResearchPlanningResult(validator.validate(draft), "LLM_VALIDATED", null, null);
        } catch (IllegalArgumentException exception) {
            return fallback(input, "PLAN_REJECTED", safeDetail(exception.getMessage()));
        } catch (Exception exception) {
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getClass().getSimpleName() + "：" + exception.getMessage();
            return fallback(input, "PLAN_REJECTED", safeDetail(detail));
        }
    }

    private ResearchPlanningResult fallback(ResearchPlanningInput input, String reason, String detail) {
        ResearchMissionDraft fallback = validator.validate(deterministicPlanner.plan(input));
        return new ResearchPlanningResult(fallback, "DETERMINISTIC", reason, detail);
    }

    private String systemPrompt() {
        return "你是 FinScope 的受控研究规划器。只负责把研究命题拆成有限任务图，不执行工具。"
                + "只能使用用户消息中列出的工具编码，不得输出HTTP地址、SQL、Shell、文件路径或额外字段。"
                + "必须返回单个JSON对象，不要Markdown。任务不超过8个，公开搜索不超过4个，依赖必须无环。"
                + "必须包含BASELINE、SUPPORT、COUNTER、ASSESS、SYNTHESIS意图。"
                + "JSON字段严格为scopeSummary、successCriteria、tasks；任务字段严格为taskKey、title、question、"
                + "taskType、toolCode、intent、dependencies、parallelGroup、queryText、rationale、expectedEvidence。";
    }

    private String userPrompt(ResearchPlanningInput input) {
        StringBuilder value = new StringBuilder();
        value.append("当前日期：").append(input.getCurrentDate()).append('\n')
                .append("研究问题：").append(compact(input.getQuestion(), 180)).append('\n')
                .append("对象类型：").append(compact(input.getSubjectType(), 40)).append('\n')
                .append("研究对象：").append(compact(input.getSubjectName(), 100)).append('\n')
                .append("主题代码：").append(input.getThemeCodes()).append('\n')
                .append("最大外部动作预算：").append(input.getMaxActions()).append("\n可用工具：\n");
        List<ResearchToolDescriptor> tools = toolRegistry.list();
        for (ResearchToolDescriptor tool : tools) {
            value.append("- ").append(tool.getCode()).append("：")
                    .append(tool.getName()).append("；").append(tool.getDescription())
                    .append("；预算=").append(tool.getBudgetType()).append('\n');
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
}
