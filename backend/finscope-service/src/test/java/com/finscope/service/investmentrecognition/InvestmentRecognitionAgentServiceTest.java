package com.finscope.service.investmentrecognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.investmentrecognition.InvestmentRecognitionCandidateRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionRun;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.service.instrument.WatchlistService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentRecognitionAgentServiceTest {
    @Test
    void generatesFallbackCandidateFromStructuredQuoteWithoutArticles() throws Exception {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertEquals(1, result.getCheckedObjects());
        assertEquals(1, result.getCandidates().size());
        assertEquals("CANDIDATE", result.getCandidates().get(0).getStatus());
        assertFalse(result.getCandidates().get(0).getSupportingData().isEmpty());
        verify(llm, never()).complete(any(), any(), any(Integer.class), any(Integer.class));
        verify(runs).record(any());
    }

    @Test
    void sendsOnlyStructuredInvestmentSnapshotToTheModel() throws Exception {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("不得检索或引用文章"), contains("600519"), any(Integer.class), any(Integer.class)))
                .thenReturn("{\"thesis\":\"盈利预期是否同步改善值得验证\",\"mechanism\":\"盈利上修可能消化估值压力\","
                        + "\"counterData\":[\"单日价格可能由情绪驱动\"],\"validationMetrics\":[\"下一期收入增速\"],"
                        + "\"invalidationConditions\":\"盈利预期不升反降\",\"horizon\":\"下一财报期\",\"confidence\":\"MEDIUM\"}");
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertEquals("盈利预期是否同步改善值得验证", result.getCandidates().get(0).getThesis());
        verify(llm).complete(contains("不得检索或引用文章"), contains("\"subjectCode\":\"600519\""),
                any(Integer.class), any(Integer.class));
    }

    @Test
    void recordsInvalidMarketDataAsNeedsEvidenceWithoutCallingTheModel() throws Exception {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(null, false)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertEquals("NEEDS_EVIDENCE", result.getCandidates().get(0).getStatus());
        assertEquals("MISSING", result.getCandidates().get(0).getEvidenceCompleteness());
        verify(llm, never()).complete(any(), any(), any(Integer.class), any(Integer.class));
    }

    private InvestmentRecognitionAgentService service(WatchlistService watchlist,
                                                       InvestmentRecognitionCandidateRepository candidates,
                                                       AgentRunRepository runs,
                                                       LlmChatClient llm) {
        return new InvestmentRecognitionAgentService(watchlist, candidates, runs, llm, new ObjectMapper());
    }

    private WatchlistItemView view(Double changePct, boolean valid) {
        WatchlistItem item = new WatchlistItem();
        item.setId(1L);
        item.setType("STOCK");
        item.setCode("600519");
        item.setName("贵州茅台");
        Quote quote = new Quote();
        quote.setInstrumentCode("600519");
        quote.setName("贵州茅台");
        quote.setPrice(1500D);
        quote.setChangePct(changePct);
        quote.setTurnover(12D);
        quote.setValid(valid);
        quote.setAsOf(LocalDateTime.of(2026, 8, 1, 10, 0));
        return new WatchlistItemView(item, quote, null);
    }
}
