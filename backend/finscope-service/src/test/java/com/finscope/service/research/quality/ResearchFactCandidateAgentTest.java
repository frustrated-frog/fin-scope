package com.finscope.service.research.quality;

import com.finscope.domain.research.quality.ResearchEvidenceDocument;
import com.finscope.domain.research.quality.ResearchTaskContract;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResearchFactCandidateAgentTest {
    @Test
    void dropsNullAndIncompleteModelItemsWithoutBreakingRuleExtraction() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(1_800))).thenReturn(
                "[null,{}, {\"evidenceRef\":\"a\",\"kind\":\"EVENT\",\"quote\":\"长鑫科技披露大宗交易。\"}]");
        ResearchFactCandidateAgent agent = agent(llm);
        assertEquals(1, agent.propose(new ResearchTaskContract(), List.of(document())).size());
        verify(llm, times(1)).complete(anyString(), anyString(), eq(20_000), eq(1_800));
    }

    @Test
    void returnsNoModelCandidatesAfterTimeoutOrInvalidJson() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(1_800)))
                .thenThrow(new java.net.SocketTimeoutException()).thenReturn("invalid json");
        ResearchFactCandidateAgent agent = agent(llm);
        assertTrue(agent.propose(new ResearchTaskContract(), List.of(document())).isEmpty());
        assertTrue(agent.propose(new ResearchTaskContract(), List.of(document())).isEmpty());
    }

    @Test
    void doesNotCallModelWithoutSelectedFullText() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        ResearchEvidenceDocument doc = document();
        doc.setSnippetOnly(true);
        assertTrue(agent(llm).propose(new ResearchTaskContract(), List.of(doc)).isEmpty());
        verifyNoInteractions(llm);
    }

    private ResearchFactCandidateAgent agent(LlmChatClient llm) {
        ResearchFactCandidateAgent agent = new ResearchFactCandidateAgent();
        ReflectionTestUtils.setField(agent, "llm", llm);
        return agent;
    }

    private ResearchEvidenceDocument document() {
        ResearchEvidenceDocument doc = new ResearchEvidenceDocument();
        doc.setReference("a");
        doc.setSelected(true);
        doc.setText("长鑫科技披露大宗交易。");
        return doc;
    }
}
