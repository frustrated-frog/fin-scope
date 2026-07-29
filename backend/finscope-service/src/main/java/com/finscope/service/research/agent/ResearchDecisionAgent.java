package com.finscope.service.research.agent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

@Component
public class ResearchDecisionAgent {
    static final int TIMEOUT_MS = 20_000;
    static final int MAX_OUTPUT_TOKENS = 1_200;
    private static final int MAX_RAW_CHARACTERS = 16_000;

    private final LlmChatClient llmChatClient;
    private final ResearchDecisionValidator validator;
    private final DeterministicResearchPolicy fallbackPolicy;
    private final ObjectMapper objectMapper;

    public ResearchDecisionAgent(LlmChatClient llmChatClient,
                                 ResearchDecisionValidator validator,
                                 DeterministicResearchPolicy fallbackPolicy) {
        this.llmChatClient = llmChatClient;
        this.validator = validator;
        this.fallbackPolicy = fallbackPolicy;
        this.objectMapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public ResearchDecisionResult decide(ResearchDecisionContext context) {
        if (context != null && context.getLatestGap() != null && context.getLatestGap().isSufficient()) {
            return new ResearchDecisionResult(fallbackPolicy.decide(context), null, null);
        }
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            return fallback(context, "MODEL_DISABLED", null);
        }
        try {
            String raw = llmChatClient.complete(systemPrompt(), context.getPrompt(),
                    TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            if (raw == null || raw.trim().isEmpty()) {
                throw new IllegalArgumentException("模型返回空决策");
            }
            if (raw.length() > MAX_RAW_CHARACTERS) {
                throw new IllegalArgumentException("模型决策超过字符上限");
            }
            ResearchDecisionDraft draft = objectMapper.readValue(raw.trim(), ResearchDecisionDraft.class);
            ResearchAgentDecision decision = validator.validate(draft, context, "MODEL");
            return new ResearchDecisionResult(decision, null, null);
        } catch (Exception error) {
            if (isTimeout(error)) {
                return fallback(context, "MODEL_TIMEOUT", "模型决策响应超时，已切换规则决策");
            }
            return fallback(context, "DECISION_REJECTED", safeDetail(error));
        }
    }

    private ResearchDecisionResult fallback(ResearchDecisionContext context, String reason, String detail) {
        return new ResearchDecisionResult(fallbackPolicy.decide(context), reason, detail);
    }

    private String systemPrompt() {
        return "你是 FinScope 的研究决策 Agent。每次只选择一个下一步动作，不输出思维链。"
                + "必须返回单个 JSON 对象，不要 Markdown，不得增加字段。"
                + "字段仅允许 decisionType、currentSubgoal、toolCode、arguments、targetGap、"
                + "expectedObservation、decisionSummary、confidence、planPatch。"
                + "decisionType 仅允许 TOOL_CALL、PLAN_PATCH、FINISH、ABORT。"
                + "可执行工具仅允许 public_news_search 和 evidence_assess。"
                + "decisionSummary 只写可审计的选择依据，不写详细内部推理过程。";
    }

    private String safeDetail(Exception error) {
        String detail = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + "：" + error.getMessage();
        detail = detail.replaceAll("[\\r\\n\\t]+", " ").trim();
        return detail.length() <= 480 ? detail : detail.substring(0, 480);
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
