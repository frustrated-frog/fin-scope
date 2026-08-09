package com.finscope.service.learningcard;

import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockLearningCardAgentExecutorTest {
    @Test
    void keepsSuccessfulDimensionsWhenOneDimensionFails() throws Exception {
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        SearchEvidenceGateway search = mock(SearchEvidenceGateway.class);
        SearchEvidenceContentService content = mock(SearchEvidenceContentService.class);
        StockLearningCardSynthesisAgent synthesis = mock(StockLearningCardSynthesisAgent.class);
        StockLearningCardAgentExecutor executor = new StockLearningCardAgentExecutor(cards, search, content,
                synthesis, Runnable::run);
        SearchEvidence hit = new SearchEvidence();
        hit.setTitle("公司公告"); hit.setUrl("https://example.com/report"); hit.setContent("公开资料摘要");
        when(search.search(any())).thenReturn(new SearchEvidenceBatch(
                Collections.singletonList(hit), Collections.emptyList(), false));
        when(content.acquire(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new ResearchEvidenceAcquisitionResult("公开资料正文", "公开资料摘要",
                        "FULL_TEXT", "test", "SUCCESS", 6));
        when(synthesis.synthesize(anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> {
                    String dimension = invocation.getArgument(2);
                    if ("COMPETITION".equals(dimension)) throw new IllegalArgumentException("invalid model output");
                    StockLearningCardClaim claim = new StockLearningCardClaim();
                    claim.setDimensionCode(dimension); claim.setStatus("READY");
                    claim.setJudgment("形成学习判断"); claim.setRationale("依据公开资料");
                    claim.setCounterargument("仍需核对反方"); claim.setUnknowns("仍有未知项"); claim.setConfidence("MEDIUM");
                    return claim;
                });
        when(cards.updateRun(any(), anyList(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Instrument instrument = new Instrument();
        instrument.setCode("603618"); instrument.setName("杭电股份");
        StockLearningCardRun run = new StockLearningCardRun();
        run.setId(9L); run.setCardId(2L); run.setStatus("RUNNING"); run.setStage("QUEUED");

        executor.execute(instrument, run);

        ArgumentCaptor<StockLearningCardRun> runs = ArgumentCaptor.forClass(StockLearningCardRun.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLearningCardClaim>> claims = ArgumentCaptor.forClass(List.class);
        verify(cards, atLeastOnce()).updateRun(runs.capture(), claims.capture(), anyList());
        StockLearningCardRun completed = runs.getAllValues().get(runs.getAllValues().size() - 1);
        List<StockLearningCardClaim> completedClaims = claims.getAllValues().get(claims.getAllValues().size() - 1);
        assertEquals("DEGRADED", completed.getStatus());
        assertEquals("COMPLETED", completed.getStage());
        assertEquals(6, completedClaims.size());
        assertEquals(1, completedClaims.stream().filter(value -> "FAILED".equals(value.getStatus())).count());
        assertTrue(completed.isRetryable());
        verify(search, org.mockito.Mockito.times(6)).search(any());
    }

    @Test
    void treatsEvidenceInsufficiencyAsADegradedLearningResultInsteadOfATechnicalFailure() throws Exception {
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        SearchEvidenceGateway search = mock(SearchEvidenceGateway.class);
        SearchEvidenceContentService content = mock(SearchEvidenceContentService.class);
        StockLearningCardSynthesisAgent synthesis = mock(StockLearningCardSynthesisAgent.class);
        StockLearningCardAgentExecutor executor = new StockLearningCardAgentExecutor(cards, search, content,
                synthesis, Runnable::run);
        when(search.search(any())).thenReturn(new SearchEvidenceBatch(Collections.emptyList(), Collections.emptyList(), false));
        when(synthesis.synthesize(anyString(), anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            StockLearningCardClaim claim = new StockLearningCardClaim();
            claim.setDimensionCode(invocation.getArgument(2)); claim.setStatus("INSUFFICIENT_EVIDENCE");
            claim.setJudgment("证据不足，暂不形成判断"); claim.setRationale("没有足够公开资料");
            claim.setCounterargument("待补充"); claim.setUnknowns("保持未知"); claim.setConfidence("LOW");
            return claim;
        });
        when(cards.updateRun(any(), anyList(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Instrument instrument = new Instrument(); instrument.setCode("603618"); instrument.setName("杭电股份");
        StockLearningCardRun run = new StockLearningCardRun(); run.setId(9L); run.setCardId(2L);

        executor.execute(instrument, run);

        assertEquals("DEGRADED", run.getStatus());
        assertEquals("INSUFFICIENT_EVIDENCE", run.getErrorCode());
        assertEquals("COMPLETED", run.getStage());
    }
}
