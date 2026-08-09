package com.finscope.service.learningcard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockLearningCardSynthesisAgentTest {
    @Test
    void rejectsFieldsOutsideTheLearningCardContract() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn("{\"judgment\":\"判断\",\"rationale\":\"依据[E1]\","
                + "\"counterargument\":\"反方\",\"unknowns\":\"未知\",\"confidence\":\"LOW\",\"theme\":\"不应出现\"}");
        StockLearningCardSynthesisAgent agent = new StockLearningCardSynthesisAgent(llm, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> agent.synthesize("杭电股份", "603618", "SPACE",
                Collections.singletonList(new StockLearningCardEvidence("E1", "公告", "url", "source", "", "正文"))));
    }

    @Test
    void returnsAnExplicitInsufficientResultWhenTheModelIsUnavailable() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);
        StockLearningCardSynthesisAgent agent = new StockLearningCardSynthesisAgent(llm, new ObjectMapper());

        StockLearningCardClaim claim = agent.synthesize("杭电股份", "603618", "SPACE",
                Collections.singletonList(new StockLearningCardEvidence("E1", "公告", "url", "source", "", "正文")));

        assertEquals("INSUFFICIENT_EVIDENCE", claim.getStatus());
        assertEquals("LOW", claim.getConfidence());
    }
}
