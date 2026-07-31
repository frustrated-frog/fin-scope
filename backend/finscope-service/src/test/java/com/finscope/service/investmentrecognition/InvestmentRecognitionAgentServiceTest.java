package com.finscope.service.investmentrecognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.investmentrecognition.InvestmentRecognitionCandidateRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionRun;
import com.finscope.domain.radar.RadarEvent;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void usesStoredQuickNewsOnlyAsATriggerAndNeverAsSupportingData() {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        RadarRepository radar = mock(RadarRepository.class);
        RadarEvent event = new RadarEvent();
        event.setCanonicalTitle("贵州茅台披露经营快讯");
        event.setSummary("市场关注后续经营数据");
        event.setWatchlistExplanation("命中自选：贵州茅台");
        when(radar.findRanked("ALL", true, 30)).thenReturn(Collections.singletonList(event));
        when(llm.isConfigured()).thenReturn(false);
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = new InvestmentRecognitionAgentService(
                watchlist, candidates, runs, radar, llm, new ObjectMapper()).run();

        assertEquals("贵州茅台披露经营快讯", result.getCandidates().get(0).getTriggerSummary());
        assertFalse(result.getCandidates().get(0).getSupportingData().contains("贵州茅台披露经营快讯"));
        assertTrue(result.getCandidates().get(0).getSupportingData().stream().allMatch(value -> !value.contains("快讯")));
    }

    @Test
    void describesConfirmedFundNavInsteadOfFabricatingAZeroPrice() {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        WatchlistItem item = new WatchlistItem();
        item.setType("FUND");
        item.setCode("110011");
        item.setName("易方达中小盘");
        Quote quote = new Quote();
        quote.setInstrumentCode("110011");
        quote.setConfirmedNav(1.2345D);
        quote.setConfirmedNavDate("2026-07-31");
        quote.setConfirmedNavChangePct(2.1D);
        quote.setValid(true);
        quote.setAsOf(LocalDateTime.of(2026, 8, 1, 10, 0));
        when(llm.isConfigured()).thenReturn(false);
        when(watchlist.listInvestmentItemsWithQuotes(false))
                .thenReturn(Collections.singletonList(new WatchlistItemView(item, quote, null)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertTrue(result.getCandidates().get(0).getObservedChange().contains("确认单位净值 1.2345（2026-07-31）"));
        assertFalse(result.getCandidates().get(0).getObservedChange().contains("最新价格 0.00"));
    }

    @Test
    void createsDifferentFingerprintsWhenTheObservationDirectionReverses() {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);
        when(watchlist.listInvestmentItemsWithQuotes(false))
                .thenReturn(Collections.singletonList(view(3.2, true)))
                .thenReturn(Collections.singletonList(view(-3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String rising = service(watchlist, candidates, runs, llm).run().getCandidates().get(0).getFingerprint();
        String falling = service(watchlist, candidates, runs, llm).run().getCandidates().get(0).getFingerprint();

        assertFalse(rising.equals(falling));
        assertTrue(rising.endsWith("UP_3_TO_5"));
        assertTrue(falling.endsWith("DOWN_3_TO_5"));
    }

    @Test
    void fallsBackWhenTheModelReturnsOnlyBlankEvidenceItems() throws Exception {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn("{\"thesis\":\"命题\",\"mechanism\":\"机制\",\"counterData\":[\" \"],"
                        + "\"validationMetrics\":[\" \"],\"invalidationConditions\":\"失效\","
                        + "\"horizon\":\"五日\",\"confidence\":\"MEDIUM\"}");
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertEquals(2, result.getCandidates().get(0).getCounterData().size());
        assertEquals(3, result.getCandidates().get(0).getValidationMetrics().size());
    }

    @Test
    void acceptsGlmSingleStringsForArrayFields() throws Exception {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn("```json\n{\"thesis\":\"命题\",\"mechanism\":\"机制\","
                        + "\"counterData\":\"单项反证\",\"validationMetrics\":\"单项指标\","
                        + "\"invalidationConditions\":\"失效\",\"horizon\":\"五日\","
                        + "\"confidence\":\"MEDIUM\"}\n```");
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertEquals(Collections.singletonList("单项反证"), result.getCandidates().get(0).getCounterData());
        assertEquals(Collections.singletonList("单项指标"), result.getCandidates().get(0).getValidationMetrics());
    }

    @Test
    void givesTheModelAnExplicitJsonArrayContract() throws Exception {
        WatchlistService watchlist = mock(WatchlistService.class);
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(argThat(prompt -> prompt.contains("\"counterData\":[\"反证项\"]")
                        && prompt.contains("\"validationMetrics\":[\"验证指标\"]")),
                any(), any(Integer.class), any(Integer.class)))
                .thenReturn("{\"thesis\":\"命题\",\"mechanism\":\"机制\","
                        + "\"counterData\":[\"反证\"],\"validationMetrics\":[\"指标\"],"
                        + "\"invalidationConditions\":\"失效\",\"horizon\":\"五日\","
                        + "\"confidence\":\"MEDIUM\"}");
        when(watchlist.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.singletonList(view(3.2, true)));
        when(candidates.saveOrRefresh(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InvestmentRecognitionRun result = service(watchlist, candidates, runs, llm).run();

        assertEquals("命题", result.getCandidates().get(0).getThesis());
    }

    private InvestmentRecognitionAgentService service(WatchlistService watchlist,
                                                       InvestmentRecognitionCandidateRepository candidates,
                                                       AgentRunRepository runs,
                                                       LlmChatClient llm) {
        RadarRepository radar = mock(RadarRepository.class);
        when(radar.findRanked("ALL", true, 30)).thenReturn(Collections.emptyList());
        return new InvestmentRecognitionAgentService(watchlist, candidates, runs, radar, llm, new ObjectMapper());
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
