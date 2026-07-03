package com.finscope.service.agent;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentBudgetPolicy;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.agent.AgentRunContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentTraceServiceTest {
    @Test
    void recordsNodeResultWithFingerprintAttemptAndBudgetSnapshot() {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        AgentTraceService service = new AgentTraceService(repository);
        AgentRunContext context = AgentRunContext.start(901L, AgentBudgetPolicy.defaults());
        AgentActionFingerprint fingerprint = AgentActionFingerprint.of(
                "source-fetch", "source", "12", "source-fetch:source:12", "input-12");
        context.enterNode(fingerprint.getNodeName());
        context.recordAction(fingerprint);
        AgentNodeResult<String> result = AgentNodeResult.success(
                "ok", "sourceId=12", "success=3, duplicate=1", 3);

        service.recordNode(null, null, context, fingerprint, result, 17L, "{\"sourceId\":12}");

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(repository).record(captor.capture());
        AgentRun saved = captor.getValue();
        assertEquals(901L, saved.getResearchRunId());
        assertEquals("source-fetch", saved.getNodeName());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("sourceId=12", saved.getInput());
        assertEquals("success=3, duplicate=1", saved.getOutput());
        assertEquals("source-fetch:source:12", saved.getStepId());
        assertEquals(1, saved.getAttempt());
        assertEquals("source-fetch:source:12", saved.getActionFingerprint());
        assertEquals("input-12", saved.getInputHash());
        assertFalse(saved.isFallbackUsed());
        assertEquals(3, saved.getProgressDelta());
        assertEquals(17L, saved.getDurationMs());
        assertEquals("{\"sourceId\":12}", saved.getMetadataJson());
        assertEquals("{\"nodeCount\":1,\"llmCallCount\":0,\"warningCount\":0}",
                saved.getBudgetSnapshot());
    }
}
