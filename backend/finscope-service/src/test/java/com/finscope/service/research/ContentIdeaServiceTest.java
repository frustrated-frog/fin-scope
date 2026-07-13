package com.finscope.service.research;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.domain.research.ContentIdea;
import com.finscope.domain.research.EventCluster;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentIdeaServiceTest {
    @Test
    void batchResearchBuildsDeterministicIdeasWithoutPerArticleLlmCall() throws Exception {
        ContentIdeaRepository ideas = mock(ContentIdeaRepository.class);
        EvidenceService evidence = mock(EvidenceService.class);
        AgentRunRepository agentRuns = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        ContentIdeaService service = new ContentIdeaService();
        ReflectionTestUtils.setField(service, "contentIdeaRepository", ideas);
        ReflectionTestUtils.setField(service, "evidenceService", evidence);
        ReflectionTestUtils.setField(service, "agentRunRepository", agentRuns);
        ReflectionTestUtils.setField(service, "llmChatClient", llm);
        EventCluster event = event();
        when(evidence.listByEventId(event.getId())).thenReturn(Collections.emptyList());
        when(llm.isConfigured()).thenReturn(true);
        ResearchRunContext.setCurrentRunId(80L);
        try {
            service.generateIfAbsent(event, null, true);

            verify(llm, never()).complete(anyString(), anyString());
            verify(ideas, atLeastOnce()).save(any(ContentIdea.class));
        } finally {
            ResearchRunContext.clear();
        }
    }

    private EventCluster event() {
        EventCluster event = new EventCluster();
        event.setId(101L);
        event.setCanonicalTitle("半导体产业资本开支持续增长");
        event.setThemeCode("company_ipo");
        event.setSummary("产业链公司继续增加资本开支。 ");
        return event;
    }
}
