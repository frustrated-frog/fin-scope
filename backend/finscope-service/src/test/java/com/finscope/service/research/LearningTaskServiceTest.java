package com.finscope.service.research;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.LearningTask;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningTaskServiceTest {
    private LearningTaskRepository tasks;
    private EvidenceService evidence;
    private AgentRunRepository agentRuns;
    private LlmChatClient llm;
    private LearningTaskService service;

    @BeforeEach
    void setUp() {
        tasks = mock(LearningTaskRepository.class);
        evidence = mock(EvidenceService.class);
        agentRuns = mock(AgentRunRepository.class);
        llm = mock(LlmChatClient.class);
        service = new LearningTaskService();
        ReflectionTestUtils.setField(service, "learningTaskRepository", tasks);
        ReflectionTestUtils.setField(service, "evidenceService", evidence);
        ReflectionTestUtils.setField(service, "agentRunRepository", agentRuns);
        ReflectionTestUtils.setField(service, "llmChatClient", llm);
    }

    @Test
    void generatesAtMostThreeAgentSuggestionsWithStableSha256Keys() throws Exception {
        EventCluster event = event();
        when(tasks.countByEventId(event.getId())).thenReturn(7);
        when(evidence.listByEventId(event.getId())).thenReturn(Collections.emptyList());
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString())).thenReturn(
                "{\"tasks\":[" +
                        "{\"question\":\"What is Alpha?\",\"difficulty\":\"FOUNDATION\"}," +
                        "{\"question\":\"  what   IS alpha?  \",\"difficulty\":\"INTERMEDIATE\"}," +
                        "{\"question\":\"What is beta?\",\"difficulty\":\"ADVANCED\"}," +
                        "{\"question\":\"What is gamma?\",\"difficulty\":\"FOUNDATION\"}]}"
        );
        when(tasks.insertSuggestionIfAbsent(any(LearningTask.class)))
                .thenReturn(true, false, true);

        service.generateIfAbsent(event, null, true);

        ArgumentCaptor<LearningTask> captor = ArgumentCaptor.forClass(LearningTask.class);
        verify(tasks, times(3)).insertSuggestionIfAbsent(captor.capture());
        List<LearningTask> generated = captor.getAllValues();
        for (LearningTask task : generated) {
            assertEquals("SUGGESTED", task.getStatus());
            assertEquals("AGENT", task.getOrigin());
            assertEquals(50, task.getPriority());
            assertTrue(task.getTaskKey().matches("[0-9a-f]{64}"));
        }
        assertEquals("4639f9d5cf332709b64e2fb40bb729b1d817c8303a2a10a6b4a2eb60b84535b2",
                generated.get(0).getTaskKey());
        assertEquals(generated.get(0).getTaskKey(), generated.get(1).getTaskKey());
        assertNotEquals(generated.get(0).getTaskKey(), generated.get(2).getTaskKey());
    }

    @Test
    void batchResearchBuildsDeterministicSuggestionsWithoutPerArticleLlmCall() throws Exception {
        EventCluster event = event();
        when(evidence.listByEventId(event.getId())).thenReturn(Collections.emptyList());
        when(llm.isConfigured()).thenReturn(true);
        when(tasks.insertSuggestionIfAbsent(any(LearningTask.class))).thenReturn(true);
        ResearchRunContext.setCurrentRunId(79L);
        try {
            service.generateIfAbsent(event, null, true);

            verify(llm, never()).complete(anyString(), anyString());
            verify(tasks, atLeastOnce()).insertSuggestionIfAbsent(any(LearningTask.class));
        } finally {
            ResearchRunContext.clear();
        }
    }

    private EventCluster event() {
        EventCluster event = new EventCluster();
        event.setId(99L);
        event.setCanonicalTitle("Alpha launch");
        event.setThemeCode("AI_STARTUP");
        event.setSummary("Alpha was launched");
        return event;
    }
}
