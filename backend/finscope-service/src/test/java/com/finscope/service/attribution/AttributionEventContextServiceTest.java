package com.finscope.service.attribution;

import com.finscope.common.enums.attribution.EventContextStatus;
import com.finscope.common.enums.attribution.EventResearchFramework;
import com.finscope.common.enums.attribution.NewsImpactDirection;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.attribution.AttributionEventContext;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AttributionEventContextServiceTest {
    private AttributionEventContextService service;
    private LlmChatClient llm;
    private SearchEvidenceGateway search;
    private AttributionReport report;
    private Instrument instrument;
    private AttributionEvidence original;

    @BeforeEach
    void setup() {
        service = new AttributionEventContextService();
        llm = mock(LlmChatClient.class);
        search = mock(SearchEvidenceGateway.class);
        when(llm.isConfigured()).thenReturn(true);
        ReflectionTestUtils.setField(service, "llmChatClient", llm);
        ReflectionTestUtils.setField(service, "searchEvidenceGateway", search);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-24"));
        report.setSummary("原摘要不可改写");
        instrument = new Instrument();
        instrument.setCode("600000");
        instrument.setName("测试公司");
        original = new AttributionEvidence();
        original.setUrl("https://example.com/original");
        original.setTitle("原始协议");
        original.setPublishedAt("2026-08-01");
        original.setSnippet("此前签署合作意向，尚未确认实际订单规模。");
    }

    @ParameterizedTest
    @CsvSource({"EARNINGS,业绩预告", "ORDER,正式合同", "GOVERNANCE,协议", "PRODUCT_PRICE,原材料成本"})
    void researchesDifferentEventQuestionsAndKeepsHistoricalContext(String framework, String question) throws Exception {
        when(search.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        SearchEvidence hit = new SearchEvidence();
        hit.setUrl("https://example.com/progress");
        hit.setPublishedAt("2026-09-23");
        hit.setTitle("实质进展");
        hit.setContent("新增正式订单和交付安排。");
        when(search.search(any())).thenReturn(new SearchEvidenceBatch(List.of(hit), List.of(), false));
        when(llm.complete(anyString(), anyString())).thenReturn(plan(framework), answer(framework));
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(EventContextStatus.COMPLETE, result.getStatus());
        assertEquals(EventResearchFramework.valueOf(framework), result.getEvents().get(0).getFramework());
        assertEquals(NewsImpactDirection.POSITIVE, result.getEvents().get(0).getImpactDirection());
        assertEquals(1, result.getSources().size());
        assertEquals(original.getUrl(), result.getSources().get(0).getUrl());
        assertEquals(2, result.getSearchCount());
        assertEquals("原摘要不可改写", report.getSummary());
        assertFalse(original.isHistoricalContext());
        ArgumentCaptor<SearchEvidenceRequest> queries = ArgumentCaptor.forClass(SearchEvidenceRequest.class);
        verify(search, times(2)).search(queries.capture());
        assertTrue(queries.getAllValues().get(1).getQuery().contains(question));
        assertTrue(queries.getValue().getQuery().length() < 100);
        assertFalse(queries.getValue().getQuery().contains("2026-09-21 至"));
    }

    @Test
    void filtersFutureSourcesAndMilestonesButRetainsUnknownDatesAndAnalysis() throws Exception {
        when(search.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        SearchEvidence future = new SearchEvidence();
        future.setTitle("不能进入模型的未来来源");
        future.setUrl("https://example.com/future");
        future.setPublishedAt("Fri, 25 Sep 2026 09:00:00 GMT");
        future.setContent("未来内容");
        when(search.search(any())).thenReturn(new SearchEvidenceBatch(List.of(future), List.of(), false));
        when(llm.complete(anyString(), anyString())).thenReturn(plan("ORDER"), """
                {"summary":"有背景线索", "events":[{"title":"订单进展","impactDirection":"POSITIVE",
                 "sourceIds":["S1","fake"],"timeline":[
                  {"date":"2026-09-25","description":"未来事件","sourceIds":["S1"]},
                  {"date":null,"description":"日期未知的披露","sourceIds":["S1"]},
                  {"date":"2026-09-23","description":"编造引用","sourceIds":["fake"]}]}]}
                """);
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(EventContextStatus.PARTIAL, result.getStatus());
        assertEquals(1, result.getSources().size());
        assertEquals(List.of("S1"), result.getEvents().get(0).getSourceIds());
        assertEquals(1, result.getEvents().get(0).getTimeline().size());
        assertNull(result.getEvents().get(0).getTimeline().get(0).getDate());
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(llm, times(2)).complete(anyString(), prompts.capture());
        assertFalse(prompts.getValue().contains("不能进入模型的未来来源"));
        assertEquals(NewsImpactDirection.POSITIVE, result.getEvents().get(0).getImpactDirection());
    }

    @Test
    void failedSearchStillAnalysesAvailableMaterial() throws Exception {
        when(search.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        when(search.search(any())).thenThrow(new IllegalStateException("offline"));
        when(llm.complete(anyString(), anyString())).thenReturn(plan("ORDER"), answer("ORDER"));
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(EventContextStatus.PARTIAL, result.getStatus());
        assertEquals(1, result.getEvents().size());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void noClearEventDoesNotInventOneOrTriggerGenericSearch() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("{\"events\":[]}",
                "{\"summary\":\"当前仅发现市场共振线索，未发现可串联的公司事项\",\"events\":[]}");
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(EventContextStatus.PARTIAL, result.getStatus());
        assertTrue(result.getEvents().isEmpty());
        verifyNoInteractions(search);
        assertTrue(result.getSummary().contains("市场共振"));
    }

    @Test
    void malformedModelDoesNotChangeOriginalReportAndKeepsSources() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("not json");
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(EventContextStatus.UNAVAILABLE, result.getStatus());
        assertEquals("原摘要不可改写", report.getSummary());
        assertEquals(1, result.getSources().size());
    }

    @Test
    void unconfiguredModelAndEmptyInputAreExplicitlyUnavailable() {
        when(llm.isConfigured()).thenReturn(false);
        assertEquals(EventContextStatus.UNAVAILABLE, service.research(report, instrument, List.of(original)).getStatus());
        when(llm.isConfigured()).thenReturn(true);
        assertEquals(EventContextStatus.UNAVAILABLE, service.research(report, instrument, List.of()).getStatus());
        verifyNoInteractions(search);
    }

    @Test
    void capsSearchesAndAvoidsRereadingTheSamePages() throws Exception {
        when(search.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        java.util.ArrayList<SearchEvidence> hits = new java.util.ArrayList<>();
        for (int index = 0; index < 6; index++) {
            SearchEvidence hit = new SearchEvidence();
            hit.setUrl("https://example.com/page-" + index);
            hit.setTitle("测试公司进展");
            hit.setContent("事件进展内容");
            hits.add(hit);
        }
        when(search.search(any())).thenReturn(new SearchEvidenceBatch(hits, List.of(), false));
        com.finscope.service.search.evidence.SearchEvidenceContentService content = mock(com.finscope.service.search.evidence.SearchEvidenceContentService.class);
        when(content.acquire(any(), anyString(), anyString(), eq(true))).thenReturn(
                new com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult(
                        "读取到的公司事件正文", "摘要", "FULL_TEXT", "html", "SUCCESS", 20));
        ReflectionTestUtils.setField(service, "searchEvidenceContentService", content);
        String eventPlan = "{\"title\":\"合作事项\",\"framework\":\"ORDER\",\"searchAnchor\":\"测试公司合同\"}";
        when(llm.complete(anyString(), anyString())).thenReturn("{\"events\":[" + eventPlan + "," + eventPlan + "," + eventPlan + "]}", answer("ORDER"));
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(4, result.getSearchCount());
        verify(search, times(4)).search(any());
        verify(content, times(4)).acquire(any(), anyString(), anyString(), eq(true));
        verify(llm, times(2)).complete(anyString(), anyString());
    }

    @Test
    void utcPublicationAfterChinaMidnightIsFutureInformation() throws Exception {
        original.setPublishedAt("2026-09-24T18:30:00Z");
        AttributionEventContext result = service.research(report, instrument, List.of(original));
        assertEquals(EventContextStatus.UNAVAILABLE, result.getStatus());
        assertTrue(result.getSources().isEmpty());
        verify(llm, never()).complete(anyString(), anyString());
    }

    private String plan(String framework) {
        return "{\"events\":[{\"title\":\"合作协议进展\",\"framework\":\"" + framework + "\",\"searchAnchor\":\"公司合作协议\"}]}";
    }

    private String answer(String framework) {
        return "{\"summary\":\"从合作意向到正式订单\",\"events\":[{\"title\":\"合作协议进展\",\"framework\":\"" + framework
                + "\",\"priorState\":\"此前仅有意向\",\"newInformation\":\"正式签约\",\"impactDirection\":\"POSITIVE\","
                + "\"impactAnalysis\":\"收入可见度改善但取决于交付\",\"changeType\":\"SUBSTANTIVE_PROGRESS\",\"sourceIds\":[\"S1\"],"
                + "\"timeline\":[{\"date\":\"2026-08-01\",\"description\":\"签订意向\",\"sourceIds\":[\"S1\"]}]}]}";
    }
}
