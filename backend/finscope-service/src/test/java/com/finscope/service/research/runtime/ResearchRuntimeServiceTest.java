package com.finscope.service.research.runtime;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchRuntimeServiceTest {
    private final ResearchRuntimeRepository repository = mock(ResearchRuntimeRepository.class);
    private final ResearchRuntimeService service = new ResearchRuntimeService(repository, new ResearchRuntimePolicy());

    @Test
    void completedNodeIsSkippedDuringResume() {
        when(repository.hasCompletedNode(7L, "plan_sources")).thenReturn(true);

        RuntimeNodeStart start = service.startNode(7L, "plan_sources", "PLAN", "plan:7", "themes=macro");

        assertTrue(start.isAlreadyCompleted());
        assertFalse(start.isStarted());
        verify(repository, never()).startNode(any(), anyInt(), any(), any(), anyInt(), anyInt(), any(Boolean.class));
    }

    @Test
    void startNodeConsumesBudgetAndRecordsEvent() {
        ResearchRuntimeCheckpoint state = checkpoint("READY", 4);
        ResearchRuntimeCheckpoint started = checkpoint("RUNNING", 5);
        started.setConsumedActions(1);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(state), Optional.of(started));
        when(repository.countStartedActions(7L, "fetch:12")).thenReturn(0);
        when(repository.startNode(7L, 4, "COLLECT", "collect_source:12", 1, 0, false)).thenReturn(true);

        RuntimeNodeStart result = service.startNode(7L, "collect_source:12", "COLLECT", "fetch:12", "sourceId=12");

        assertTrue(result.isStarted());
        assertEquals(1, result.getCheckpoint().getConsumedActions());
        verify(repository).appendEvent(any(ResearchRuntimeEvent.class));
    }

    @Test
    void blockedActionTerminatesRuntimeWithoutStartingNode() {
        ResearchRuntimeCheckpoint state = checkpoint("RUNNING", 4);
        state.setConsumedActions(12);
        state.setMaxActions(12);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(state));
        when(repository.countStartedActions(7L, "fetch:12")).thenReturn(0);
        when(repository.terminate(7L, 4, "BUDGET_EXHAUSTED")).thenReturn(true);

        RuntimeNodeStart result = service.startNode(7L, "collect_source:12", "COLLECT", "fetch:12", "sourceId=12");

        assertFalse(result.isStarted());
        assertEquals("BUDGET_EXHAUSTED", result.getTerminationReason());
        verify(repository).terminate(7L, 4, "BUDGET_EXHAUSTED");
    }

    @Test
    void terminationEventIsNotWrittenWhenGuardCasLoses() {
        ResearchRuntimeCheckpoint state = checkpoint("RUNNING", 4);
        state.setConsumedActions(12);
        state.setMaxActions(12);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(state));
        when(repository.countStartedActions(7L, "fetch:12")).thenReturn(0);
        when(repository.terminate(7L, 4, "BUDGET_EXHAUSTED")).thenReturn(false);

        assertThrows(BusinessConflictException.class, () -> service.startNode(
                7L, "collect_source:12", "COLLECT", "fetch:12", "sourceId=12"));

        verify(repository, never()).appendEvent(any(ResearchRuntimeEvent.class));
    }

    @Test
    void reportFinalizationCanRunAfterGuardTermination() {
        ResearchRuntimeCheckpoint terminated = checkpoint("TERMINATED", 4);
        terminated.setTerminationReason("NO_PROGRESS");
        ResearchRuntimeCheckpoint finalizing = checkpoint("RUNNING", 5);
        finalizing.setTerminationReason("NO_PROGRESS");
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(terminated), Optional.of(finalizing));
        when(repository.startNode(7L, 4, "SYNTHESIZE", "compose_report", 0, 0, true)).thenReturn(true);

        RuntimeNodeStart result = service.startNode(7L, "compose_report", "SYNTHESIZE", null, "runId=7");

        assertTrue(result.isStarted());
    }

    @Test
    void nonFinalizationSystemNodeCannotEraseGuardTermination() {
        ResearchRuntimeCheckpoint finalizing = checkpoint("FINALIZING", 4);
        finalizing.setTerminationReason("NO_PROGRESS");
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(finalizing));

        RuntimeNodeStart result = service.startNode(7L, "classify_events", "ASSESS", null, "runId=7");

        assertFalse(result.isStarted());
        assertEquals("NO_PROGRESS", result.getTerminationReason());
        verify(repository, never()).startNode(any(), anyInt(), any(), any(), anyInt(), anyInt(), any(Boolean.class));
    }

    @Test
    void systemNodeCanFinalizeWithoutConsumingActionBudget() {
        ResearchRuntimeCheckpoint state = checkpoint("RUNNING", 4);
        state.setConsumedActions(12);
        state.setMaxActions(12);
        ResearchRuntimeCheckpoint started = checkpoint("RUNNING", 5);
        started.setConsumedActions(12);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(state), Optional.of(started));
        when(repository.startNode(7L, 4, "SYNTHESIZE", "compose_report", 12, 0, false)).thenReturn(true);

        RuntimeNodeStart result = service.startNode(7L, "compose_report", "SYNTHESIZE", null, "runId=7");

        assertTrue(result.isStarted());
        assertEquals(12, result.getCheckpoint().getConsumedActions());
    }

    @Test
    void configuredSourceDoesNotIncrementNoProgressForSameStateHash() {
        ResearchRuntimeCheckpoint state = checkpoint("RUNNING", 4);
        state.setLastStateHash("2:1:3:0");
        state.setNoProgressCount(1);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(state));
        when(repository.completeNode(7L, 4, "collect_source:12", "2:1:3:0", 0, 0)).thenReturn(true);

        service.completeNode(7L, "collect_source:12", "2:1:3:0", 0, "evidence=3");

        verify(repository).completeNode(7L, 4, "collect_source:12", "2:1:3:0", 0, 0);
        verify(repository).appendEvent(any(ResearchRuntimeEvent.class));
    }

    @Test
    void adaptiveMissionSearchIncrementsNoProgressForSameStateHash() {
        ResearchRuntimeCheckpoint state = checkpoint("RUNNING", 4);
        state.setLastStateHash("2:1:3:0");
        state.setNoProgressCount(1);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(state));
        when(repository.completeNode(7L, 4, "mission:support_search", "2:1:3:0", 2, 0)).thenReturn(true);

        service.completeNode(7L, "mission:support_search", "2:1:3:0", 0, "evidence=3");

        verify(repository).completeNode(7L, 4, "mission:support_search", "2:1:3:0", 2, 0);
        verify(repository).appendEvent(any(ResearchRuntimeEvent.class));
    }

    @Test
    void resumeClaimsInterruptedRunAndRejectsConcurrentClaim() {
        ResearchRuntimeCheckpoint interrupted = checkpoint("INTERRUPTED", 4);
        interrupted.setCurrentNode("collect_sources");
        ResearchRuntimeCheckpoint resumed = checkpoint("RUNNING", 5);
        resumed.setCurrentNode("collect_sources");
        resumed.setResumeCount(1);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(interrupted), Optional.of(resumed));
        when(repository.resume(7L, 4)).thenReturn(true);

        assertEquals(1, service.resume(7L).getResumeCount());

        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(resumed));
        assertThrows(BusinessConflictException.class, () -> service.resume(7L));
    }

    @Test
    void runtimeViewReportsRecoverability() {
        ResearchRuntimeCheckpoint interrupted = checkpoint("INTERRUPTED", 4);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(interrupted));
        when(repository.findEvents(7L)).thenReturn(Collections.<ResearchRuntimeEvent>emptyList());

        assertTrue(service.view(7L).isRecoverable());
    }

    @Test
    void completionDoesNotOverwriteAGuardTermination() {
        ResearchRuntimeCheckpoint terminated = checkpoint("TERMINATED", 6);
        terminated.setTerminationReason("BUDGET_EXHAUSTED");
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(terminated));

        ResearchRuntimeCheckpoint result = service.complete(7L);

        assertEquals("TERMINATED", result.getStatus());
        verify(repository, never()).completeRuntime(any(), anyInt());
    }

    @Test
    void failureClosesTheNodeRecordedInCheckpoint() {
        ResearchRuntimeCheckpoint running = checkpoint("RUNNING", 4);
        running.setCurrentNode("collect_source:12:0");
        ResearchRuntimeCheckpoint interrupted = checkpoint("INTERRUPTED", 5);
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(running), Optional.of(interrupted));
        when(repository.failNode(7L, 4, "network error")).thenReturn(true);

        service.failNode(7L, "fetch_sources", "NETWORK", "network error");

        verify(repository).appendEvent(argThat(event -> "NODE_FAILED".equals(event.getEventType())
                && "collect_source:12:0".equals(event.getNodeId())));
    }

    @Test
    void failureDoesNotEraseAnExistingHardTerminationReason() {
        ResearchRuntimeCheckpoint finalizing = checkpoint("RUNNING", 4);
        finalizing.setCurrentNode("compose_report");
        finalizing.setTerminationReason("NO_PROGRESS");
        ResearchRuntimeCheckpoint terminated = checkpoint("TERMINATED", 5);
        terminated.setTerminationReason("NO_PROGRESS");
        when(repository.findCheckpoint(7L)).thenReturn(Optional.of(finalizing), Optional.of(terminated));
        when(repository.failNode(7L, 4, "generation error")).thenReturn(true);

        ResearchRuntimeCheckpoint result = service.failNode(7L, "compose_report", "LLM", "generation error");

        assertEquals("TERMINATED", result.getStatus());
        assertEquals("NO_PROGRESS", result.getTerminationReason());
    }

    private ResearchRuntimeCheckpoint checkpoint(String status, int version) {
        ResearchRuntimeCheckpoint value = new ResearchRuntimeCheckpoint();
        value.setResearchRunId(7L);
        value.setStateVersion(version);
        value.setStatus(status);
        value.setCurrentNode("plan_sources");
        value.setMaxActions(12);
        return value;
    }
}
