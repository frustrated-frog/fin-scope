package com.finscope.service.attribution;

import com.finscope.common.enums.attribution.AttributionInsightStatus;
import com.finscope.common.enums.attribution.NewsImpactDirection;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.attribution.*;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttributionResearchInsightsServiceTest {
    private AttributionResearchInsightsService service;
    private LlmChatClient llm;
    private AttributionInsightMaterialsService materials;
    private AttributionPeerComparisonService comparisons;
    private AttributionReport report;
    private Instrument stock;

    @BeforeEach
    void setup() {
        service = new AttributionResearchInsightsService();
        llm = mock(LlmChatClient.class);
        materials = mock(AttributionInsightMaterialsService.class);
        comparisons = mock(AttributionPeerComparisonService.class);
        ReflectionTestUtils.setField(service, "llmChatClient", llm);
        ReflectionTestUtils.setField(service, "materialsService", materials);
        ReflectionTestUtils.setField(service, "comparisonService", comparisons);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        when(llm.isConfigured()).thenReturn(true);
        report = new AttributionReport();
        report.setSummary("原报告摘要");
        report.setReportDate(LocalDate.parse("2026-09-18"));
        stock = new Instrument();
        stock.setName("测试公司");
        stock.setCode("600000");
    }

    @Test
    void keepsDirectionalMechanismAnalysisWithoutMakingQuotesOutOfModelNumbers() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("""
                {"businesses":[{"business":"光纤","transmission":"涨价改善单位利润，取决于成本传导","direction":"POSITIVE","sourceIds":["fake"]}],
                 "expectations":[{"topic":"盈利弹性","priorExpectation":"此前预期平稳","newInformation":"产品涨价","direction":"POSITIVE"}],
                 "comparisons":[{"changePct":99}],"peers":[],"sectorCode":"invented"}
                """);
        var result = service.research(report, stock, List.of());
        assertEquals(NewsImpactDirection.POSITIVE, result.getBusinesses().get(0).getDirection());
        assertTrue(result.getBusinesses().get(0).getSourceIds().isEmpty());
        assertEquals(1, result.getExpectations().size());
        assertTrue(result.getComparisons().isEmpty());
        assertEquals("原报告摘要", report.getSummary());
        verify(comparisons).capture(eq(result), eq(stock), eq(report.getReportDate()), anyList(), isNull());
    }

    @Test
    void carriesSpecialistReasoningIntoBusinessAndExpectationAnalysisInOneCall() throws Exception {
        var driver = new AttributionDriver();
        var analysis = new AttributionNewsAnalysis();
        analysis.setKeyChange("交付周期影响收入确认");
        driver.setNewsAnalysis(analysis);
        report.setDrivers(List.of(driver));
        when(llm.complete(anyString(), anyString())).thenReturn("{\"businesses\":[],\"expectations\":[]}");
        service.research(report, stock, List.of());
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llm, times(1)).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("交付周期影响收入确认"));
        assertTrue(prompt.getValue().contains("不是新增事实来源"));
        assertTrue(prompt.getValue().contains("订单额不能直接当利润或当期收入"));
        assertEquals(analysis, report.getDrivers().get(0).getNewsAnalysis());
    }

    @Test
    void failedMaterialsAndQuotesDoNotDiscardGeneratedBusinessAnalysis() throws Exception {
        doThrow(new IllegalStateException("search down")).when(materials).collect(any(), any(), any(), any());
        doThrow(new IllegalStateException("quotes down")).when(comparisons).capture(any(), any(), any(), any(), any());
        when(llm.complete(anyString(), anyString())).thenReturn("{\"businesses\":[{\"business\":\"产品业务\"}],\"expectations\":[]}");
        var result = service.research(report, stock, List.of());
        assertEquals(1, result.getBusinesses().size());
        assertEquals(AttributionInsightStatus.PARTIAL, result.getStatus());
        assertEquals(3, result.getWarnings().size());
    }

    @Test
    void malformedModelStillRetrievesActualQuotes() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("invalid JSON");
        doAnswer(call -> {
            AttributionResearchInsights result = call.getArgument(0);
            var row = new AttributionComparisonRow();
            row.setChangePct(2D);
            result.getComparisons().add(row);
            return null;
        }).when(comparisons).capture(any(), any(), any(), any(), any());
        var result = service.research(report, stock, List.of());
        assertEquals(2D, result.getComparisons().get(0).getChangePct());
        assertEquals(AttributionInsightStatus.PARTIAL, result.getStatus());
        assertEquals("原报告摘要", report.getSummary());
    }

    @Test
    void unconfiguredModelDoesNotSearchButStillAttemptsComparison() throws Exception {
        when(llm.isConfigured()).thenReturn(false);
        var result = service.research(report, stock, List.of());
        assertEquals(AttributionInsightStatus.UNAVAILABLE, result.getStatus());
        verifyNoInteractions(materials);
        verify(llm, never()).complete(anyString(), anyString());
        verify(comparisons).capture(eq(result), eq(stock), eq(report.getReportDate()), anyList(), isNull());
    }
}
