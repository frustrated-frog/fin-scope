package com.finscope.service.research.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class ResearchToolDispatcher {
    private final ResearchAgentToolRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResearchToolDispatcher(ResearchAgentToolRegistry registry) {
        this.registry = registry;
    }

    public ResearchToolObservation dispatch(ResearchAgentDecision decision,
                                            ResearchAgentToolContext context) {
        if (decision == null || !"TOOL_CALL".equals(decision.getDecisionType())) {
            throw new BusinessException(BizErrorCode.RESEARCH_TOOL_ONLY_TOOL_CALL);
        }
        ResearchAgentTool tool = registry.required(decision.getToolCode());
        Map<String, Object> arguments = parseArguments(decision.getArgumentsJson());
        tool.validate(arguments);
        ResearchToolObservation observation;
        try {
            observation = tool.execute(context, arguments);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            observation = new ResearchToolObservation();
            observation.setStatus("TERMINAL_ERROR");
            observation.setObservationSummary(safe(error.getMessage(), "工具执行失败"));
            observation.setErrorType("TOOL_EXECUTION_FAILED");
            observation.setRetryable(false);
            observation.setStateHash("ERROR:" + decision.getToolCode());
        }
        if (observation == null) {
            throw new BusinessException(BizErrorCode.RESEARCH_TOOL_NO_OBSERVATION);
        }
        observation.setResearchRunId(context.getResearchRunId());
        observation.setDecisionId(context.getDecisionId());
        observation.setToolCode(decision.getToolCode());
        return observation;
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception error) {
            throw new IllegalArgumentException("持久化工具参数不是合法 JSON", error);
        }
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 320 ? compact : compact.substring(0, 320);
    }
}
