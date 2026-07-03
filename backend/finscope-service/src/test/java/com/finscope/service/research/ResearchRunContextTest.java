package com.finscope.service.research;

import com.finscope.domain.agent.AgentBudgetPolicy;
import com.finscope.domain.agent.AgentRunContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResearchRunContextTest {
    @AfterEach
    void clearContext() {
        ResearchRunContext.clear();
    }

    @Test
    void setCurrentRunIdCreatesAgentRunContextForBackwardCompatibility() {
        ResearchRunContext.setCurrentRunId(222L);

        assertEquals(222L, ResearchRunContext.currentRunId());
        assertEquals(222L, ResearchRunContext.currentContext().getResearchRunId());
    }

    @Test
    void setCurrentContextPreservesFullAgentContext() {
        AgentRunContext context = AgentRunContext.start(333L, AgentBudgetPolicy.defaults());
        context.enterNode("article-interpret");

        ResearchRunContext.setCurrentContext(context);

        assertEquals(333L, ResearchRunContext.currentRunId());
        assertSame(context, ResearchRunContext.currentContext());
        assertEquals("article-interpret", ResearchRunContext.currentContext().getCurrentNodeName());
    }

    @Test
    void clearRemovesCurrentContext() {
        ResearchRunContext.setCurrentRunId(444L);

        ResearchRunContext.clear();

        assertNull(ResearchRunContext.currentRunId());
        assertNull(ResearchRunContext.currentContext());
    }
}
