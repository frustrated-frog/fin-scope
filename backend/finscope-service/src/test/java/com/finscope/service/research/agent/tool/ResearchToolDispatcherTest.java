package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResearchToolDispatcherTest {
    @Test
    void resolvesTypedToolDynamicallyAndAttachesDecisionIdentity() {
        ResearchAgentToolRegistry registry = new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>singletonList(new FakeTool()));
        ResearchToolDispatcher dispatcher = new ResearchToolDispatcher(registry);
        ResearchAgentDecision decision = decision("test_tool", "{\"value\":\"观察输入\"}");

        ResearchToolObservation observation = dispatcher.dispatch(decision,
                new ResearchAgentToolContext(12L, 5L));

        assertEquals("test_tool", registry.required("test_tool").descriptor().getCode());
        assertEquals(12L, observation.getResearchRunId());
        assertEquals(5L, observation.getDecisionId());
        assertEquals("观察输入", observation.getNewInformation());
    }

    @Test
    void rejectsUnknownToolAndMalformedPersistedArguments() {
        ResearchToolDispatcher emptyDispatcher = new ResearchToolDispatcher(
                new ResearchAgentToolRegistry(Collections.<ResearchAgentTool>emptyList()));
        ResearchToolDispatcher typedDispatcher = new ResearchToolDispatcher(
                new ResearchAgentToolRegistry(Collections.<ResearchAgentTool>singletonList(new FakeTool())));

        assertThrows(IllegalArgumentException.class,
                () -> emptyDispatcher.dispatch(decision("shell", "{}"), new ResearchAgentToolContext(12L, 5L)));
        assertThrows(IllegalArgumentException.class,
                () -> typedDispatcher.dispatch(decision("test_tool", "not-json"),
                        new ResearchAgentToolContext(12L, 5L)));
    }

    private ResearchAgentDecision decision(String tool, String arguments) {
        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setId(5L);
        value.setResearchRunId(12L);
        value.setDecisionType("TOOL_CALL");
        value.setToolCode(tool);
        value.setArgumentsJson(arguments);
        return value;
    }

    private static class FakeTool implements ResearchAgentTool {
        @Override
        public ResearchToolDescriptor descriptor() {
            ResearchToolDescriptor value = new ResearchToolDescriptor();
            value.setCode("test_tool");
            value.setName("测试工具");
            return value;
        }

        @Override
        public void validate(Map<String, Object> arguments) {
            if (!arguments.keySet().equals(Collections.singleton("value"))) {
                throw new IllegalArgumentException("value required");
            }
        }

        @Override
        public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
            ResearchToolObservation value = new ResearchToolObservation();
            value.setStatus("SUCCESS");
            value.setObservationSummary("完成测试工具调用");
            value.setNewInformation(String.valueOf(arguments.get("value")));
            value.setStateHash("test-state");
            return value;
        }
    }
}
