package com.finscope.service.research.agent;

import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchAgentStateReducerTest {
    @Test
    void persistsObservationIntoWorkingMemoryAndNoProgressGuards() {
        ResearchAgentRepository repository = mock(ResearchAgentRepository.class);
        ResearchAgentStateReducer reducer = new ResearchAgentStateReducer(repository);
        ResearchAgentState state = state();
        when(repository.updateState(state, 3)).thenReturn(true);

        ResearchAgentDecision decision = decision("MODEL");
        ResearchToolObservation observation = observation("NO_PROGRESS", 0, 0);
        observation.setId(80L);
        reducer.reduceAndPersist(state, decision, observation);

        assertEquals(1, state.getDecisionCount());
        assertEquals(1, state.getNoProgressCount());
        assertEquals(80L, state.getLastObservationId());
        assertEquals(Collections.singletonList("fingerprint-1"), state.getAttemptedFingerprints());
        assertTrue(state.getMemorySummary().contains("没有新增证据"));
        verify(repository).updateState(state, 3);
    }

    @Test
    void resetsNoProgressAfterEvidenceDeltaAndCountsDeterministicFallback() {
        ResearchAgentRepository repository = mock(ResearchAgentRepository.class);
        ResearchAgentStateReducer reducer = new ResearchAgentStateReducer(repository);
        ResearchAgentState state = state();
        state.setNoProgressCount(2);
        when(repository.updateState(state, 3)).thenReturn(true);

        reducer.reduceAndPersist(state, decision("DETERMINISTIC"), observation("SUCCESS", 2, 1));

        assertEquals(0, state.getNoProgressCount());
        assertEquals(1, state.getFallbackCount());
        assertEquals("本轮新增 2 条证据，覆盖 1 个新来源；证据账本已更新。", state.getEvidenceSummary());
    }

    @Test
    void explainsExhaustedRetriesWithoutLeakingInternalStateHashes() {
        ResearchAgentRepository repository = mock(ResearchAgentRepository.class);
        ResearchAgentStateReducer reducer = new ResearchAgentStateReducer(repository);
        ResearchAgentState state = state();
        when(repository.updateState(state, 3)).thenReturn(true);
        ResearchToolObservation observation = observation("RETRYABLE_ERROR", 0, 0);
        observation.setAttemptCount(2);

        reducer.reduceAndPersist(state, decision("MODEL"), observation);

        assertEquals("工具自动重试 1 次后仍未恢复；本轮没有写入新证据。", state.getEvidenceSummary());
        assertTrue(!state.getEvidenceSummary().contains("state-next"));
    }

    @Test
    void recordsSafeAbortAsTerminalWorkingState() {
        ResearchAgentRepository repository = mock(ResearchAgentRepository.class);
        ResearchAgentStateReducer reducer = new ResearchAgentStateReducer(repository);
        ResearchAgentState state = state();
        ResearchAgentDecision decision = decision("DETERMINISTIC");
        decision.setDecisionType("ABORT");
        decision.setDecisionSummary("动作预算耗尽，安全终止");
        when(repository.updateState(state, 3)).thenReturn(true);

        reducer.recordAbort(state, decision);

        assertEquals("FAILED", state.getStatus());
        assertTrue(state.getMemorySummary().contains("动作预算耗尽"));
        verify(repository).updateState(state, 3);
    }

    private ResearchAgentState state() {
        ResearchAgentState value = new ResearchAgentState();
        value.setResearchRunId(11L);
        value.setStateVersion(3);
        value.setStatus("EXECUTING");
        value.setMemorySummary("此前完成基线扫描");
        value.setAttemptedFingerprints(Collections.<String>emptyList());
        return value;
    }

    private ResearchAgentDecision decision(String mode) {
        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setIteration(1);
        value.setDecisionType("TOOL_CALL");
        value.setCurrentSubgoal("补齐反方证据");
        value.setToolCode("public_news_search");
        value.setDecisionSummary("优先寻找反方材料");
        value.setActionFingerprint("fingerprint-1");
        value.setDecisionMode(mode);
        return value;
    }

    private ResearchToolObservation observation(String status, int evidenceDelta, int sourceDelta) {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setStatus(status);
        value.setObservationSummary(status.equals("NO_PROGRESS") ? "没有新增证据" : "发现新增证据");
        value.setEvidenceDelta(evidenceDelta);
        value.setSourceDelta(sourceDelta);
        value.setStateHash("state-next");
        return value;
    }
}
