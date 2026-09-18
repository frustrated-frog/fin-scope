package com.finscope.service.attribution;

import com.finscope.common.enums.attribution.AssessmentStatus;
import com.finscope.common.enums.attribution.HypothesisDisposition;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.attribution.*;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttributionAssessmentServiceTest {
    private AttributionAssessmentService service;
    private LlmChatClient llm;
    private AttributionReport report;
    private Instrument instrument;
    private AttributionEvidence evidence;
    private final String focus = "{\"researchFocus\":\"订单新增了什么\",\"focusReason\":\"核验商业化阶段\",\"missingInformation\":[\"订单金额\"]}";

    @BeforeEach
    void setup() {
        service = new AttributionAssessmentService();
        llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        ReflectionTestUtils.setField(service, "llmChatClient", llm);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "evidenceGate", new AttributionEvidenceGate());
        AttributionMarketContextService context = mock(AttributionMarketContextService.class);
        AttributionMarketContext snapshot = new AttributionMarketContext();
        snapshot.setQuoteVerified(true);
        snapshot.setStockChangePct(6D);
        when(context.capture(any(), any())).thenReturn(snapshot);
        ReflectionTestUtils.setField(service, "marketContextService", context);
        report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-21"));
        report.setChangePct(99D);
        instrument = new Instrument();
        instrument.setCode("600519");
        evidence = new AttributionEvidence();
        evidence.setUrl("https://source.test/order");
        evidence.setPublishedAt("2026-09-20");
        evidence.setStance("SUPPORT");
    }

    @Test
    void keepsWeekendSupportAndOnlyAllowsWriterToOrderApprovedFragments() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn(focus, decision(evidence.getUrl()),
                "{\"paragraphIds\":[\"judgment\",\"h1\",\"boundary\"],\"inventedFact\":\"利润增长100倍\"}");
        AttributionAssessment result = run();
        assertEquals(AssessmentStatus.COMPLETE, result.getStatus());
        assertEquals(HypothesisDisposition.PREFERRED, result.getHypotheses().get(0).getDisposition());
        assertEquals(6D, report.getChangePct());
        assertEquals(3, result.getCommentary().size());
        assertFalse(String.join("", result.getCommentary()).contains("100倍"));
        verify(llm, times(3)).complete(anyString(), anyString());
    }

    @Test
    void fabricatedUrlCannotBecomeMainExplanation() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn(focus, decision("https://invented.test/none"), "{}");
        AttributionAssessment result = run();
        assertEquals(AssessmentStatus.INSUFFICIENT_EVIDENCE, result.getStatus());
        assertEquals(HypothesisDisposition.UNRESOLVED, result.getHypotheses().get(0).getDisposition());
        assertTrue(result.getMainJudgment().contains("无法区分"));
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void counterEvidenceCannotBePromotedToPreferred() throws Exception {
        evidence.setStance("COUNTER");
        when(llm.complete(anyString(), anyString())).thenReturn(focus, decision(evidence.getUrl()), "{}");
        assertEquals(AssessmentStatus.INSUFFICIENT_EVIDENCE, run().getStatus());
    }

    @Test
    void partialModelFailureRetainsFocusAndSnapshot() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn(focus).thenThrow(new IllegalStateException("unavailable"));
        AttributionAssessment result = run();
        assertEquals(AssessmentStatus.DEGRADED, result.getStatus());
        assertEquals("订单新增了什么", result.getResearchFocus());
        assertNotNull(result.getMarketContext());
        assertTrue(result.getHypotheses().isEmpty());
        verify(llm, times(2)).complete(anyString(), anyString());
    }

    @Test
    void unconfiguredModelDoesNotInventDrivers() throws Exception {
        when(llm.isConfigured()).thenReturn(false);
        AttributionAssessment result = run();
        assertEquals(AssessmentStatus.DEGRADED, result.getStatus());
        assertTrue(result.getHypotheses().isEmpty());
        verify(llm, never()).complete(anyString(), anyString());
    }

    private AttributionAssessment run() {
        return service.research(report, instrument, Arrays.asList(evidence), report.getReportDate().minusDays(3), stage -> {});
    }

    private String decision(String url) {
        return "{\"mainJudgment\":\"商业化取得进展，尚不能等同利润兑现\",\"pricingDebate\":\"能否规模交付\","
                + "\"explainedScope\":[\"业务进展\"],\"unexplainedScope\":[\"异动时点\"],\"hypotheses\":[{"
                + "\"explanation\":\"订单进展\",\"disposition\":\"PREFERRED\",\"selectionReason\":\"有公告支持\","
                + "\"pricingMechanism\":\"商业化成功可能性变化，未有利润预测\",\"explains\":\"经营变化\",\"doesNotExplain\":\"具体涨幅\","
                + "\"evidenceUrls\":[\"" + url + "\"],\"assumptions\":[\"能够复制交付\"],\"revisionConditions\":[\"订单取消则削弱判断\"]}]}";
    }
}
