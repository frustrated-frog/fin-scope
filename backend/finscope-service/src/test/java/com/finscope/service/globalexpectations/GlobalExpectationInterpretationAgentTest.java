package com.finscope.service.globalexpectations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExpectationInterpretationAgentTest {
    @Test
    void returnsFiveBoundedFieldsFromAConfiguredModel() {
        GlobalExpectationInterpretationAgent agent = agent(true,
                "{\"happened\":\"概率快速上升\",\"meaning\":\"市场分歧扩大\","
                        + "\"relatedVariables\":\"利率与通胀\",\"nextObservation\":\"关注正式决议\","
                        + "\"uncertainty\":\"预测市场价格不等于事实概率\"}");

        GlobalExpectationInterpretation result = agent.interpret(group());

        assertEquals("READY", result.getStatus());
        assertEquals("概率快速上升", result.getHappened());
        assertEquals("关注正式决议", result.getNextObservation());
        assertEquals("预测市场价格不等于事实概率", result.getUncertainty());
        assertEquals("AI", result.getSource());
    }

    @Test
    void modelFailureReturnsUnavailableWithoutThrowingIntoTheRefreshPath() {
        GlobalExpectationInterpretation result = agent(false, "").interpret(group());

        assertEquals("UNAVAILABLE", result.getStatus());
    }

    private GlobalExpectationInterpretationAgent agent(boolean configured, String response) {
        GlobalExpectationInterpretationAgent agent = new GlobalExpectationInterpretationAgent();
        ReflectionTestUtils.setField(agent, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(agent, "llmChatClient", new LlmChatClient() {
            @Override
            public boolean isConfigured() {
                return configured;
            }

            @Override
            public String modelName() {
                return "test-model";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return response;
            }
        });
        return agent;
    }

    private GlobalExpectationEventGroup group() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setId("event:fed");
        group.setTitle("美联储利率决议");
        group.setSignalScore(80);
        group.setSignalReasons(List.of("1小时概率显著上升"));
        group.setMarkets(List.of());
        group.setRadarMatches(List.of());
        return group;
    }
}
