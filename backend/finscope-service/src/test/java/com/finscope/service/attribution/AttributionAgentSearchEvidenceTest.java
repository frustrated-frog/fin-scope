package com.finscope.service.attribution;

import com.finscope.domain.article.Article;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;

class AttributionAgentSearchEvidenceTest {
    @Test
    void usesDeepSharedSearchEvidenceForEveryLogicalQuery() {
        SearchEvidenceGateway gateway = mock(SearchEvidenceGateway.class);
        when(gateway.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        SearchEvidence evidence = new SearchEvidence();
        evidence.setTitle("公司发布最新经营公告");
        evidence.setUrl("https://example.com/company?a=1");
        evidence.setContent("公告显示订单和收入增长");
        evidence.setSourceDomain("example.com");
        evidence.setSourceTier("T1");
        evidence.setFusionScore(0.03D);
        evidence.setProviders(Arrays.asList("ANYSEARCH", "TAVILY"));
        when(gateway.search(any(SearchEvidenceRequest.class))).thenReturn(new SearchEvidenceBatch(
                Collections.singletonList(evidence), Collections.emptyList(), false));

        AttributionAgent agent = new AttributionAgent();
        ReflectionTestUtils.setField(agent, "evidenceGate", new AttributionEvidenceGate());
        ReflectionTestUtils.setField(agent, "searchEvidenceGateway", gateway);
        SearchEvidenceContentService contentService = mock(SearchEvidenceContentService.class);
        when(contentService.acquire(any(SearchEvidence.class), any(String.class), any(String.class), any(Boolean.class)))
                .thenReturn(new ResearchEvidenceAcquisitionResult("公告显示订单和收入增长", "搜索摘要",
                        "FULL_TEXT", "html:readability", "SUCCESS", 12));
        ReflectionTestUtils.setField(agent, "searchEvidenceContentService", contentService);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);
        ReflectionTestUtils.setField(agent, "llmChatClient", llm);
        ArticleRepository articles = mock(ArticleRepository.class);
        when(articles.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(agent, "articleRepository", articles);
        ReflectionTestUtils.setField(agent, "agentRunRepository", mock(AgentRunRepository.class));
        Instrument instrument = new Instrument();
        instrument.setCode("NVDA");
        instrument.setName("英伟达");
        instrument.setType("FUND");
        AttributionReport report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-18"));

        agent.research(report, instrument, 2.5D, "task-1", mock(AttributionProgressPublisher.class));

        assertTrue(report.getDrivers().isEmpty());
        assertEquals(1, report.getEvidences().size());
        assertEquals("公告显示订单和收入增长", report.getEvidences().get(0).getSnippet());
        assertEquals("T1", report.getEvidences().get(0).getSourceTier());
        ArgumentCaptor<SearchEvidenceRequest> captor = ArgumentCaptor.forClass(SearchEvidenceRequest.class);
        verify(gateway, times(2)).search(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(request -> request.getDepth() == SearchDepth.DEEP));
        assertTrue(captor.getAllValues().stream().allMatch(request -> request.getQuery().contains("2026-09-18")));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.finscope.common.enums.attribution.AssessmentStatus.class,
            names = {"COMPLETE", "INSUFFICIENT_EVIDENCE", "DEGRADED"})
    void stockAssessmentStillProducesNarrativeAndDriverCards(com.finscope.common.enums.attribution.AssessmentStatus status) throws Exception {
        SearchEvidenceGateway gateway = mock(SearchEvidenceGateway.class);
        when(gateway.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        SearchEvidence evidence = new SearchEvidence();
        evidence.setTitle("英伟达发布最新经营公告");
        evidence.setUrl("https://example.com/company?a=1");
        evidence.setContent("公告显示订单和收入增长");
        evidence.setSourceDomain("example.com");
        evidence.setSourceTier("T1");
        evidence.setFusionScore(0.03D);
        evidence.setProviders(Arrays.asList("ANYSEARCH", "TAVILY"));
        when(gateway.search(any(SearchEvidenceRequest.class))).thenReturn(new SearchEvidenceBatch(
                Collections.singletonList(evidence), Collections.emptyList(), false));

        AttributionAgent agent = new AttributionAgent();
        ReflectionTestUtils.setField(agent, "evidenceGate", new AttributionEvidenceGate());
        ReflectionTestUtils.setField(agent, "searchEvidenceGateway", gateway);
        SearchEvidenceContentService contentService = mock(SearchEvidenceContentService.class);
        when(contentService.acquire(any(SearchEvidence.class), any(String.class), any(String.class), any(Boolean.class)))
                .thenReturn(new ResearchEvidenceAcquisitionResult("公告显示订单和收入增长", "搜索摘要",
                        "FULL_TEXT", "html:readability", "SUCCESS", 12));
        ReflectionTestUtils.setField(agent, "searchEvidenceContentService", contentService);
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any(), any())).thenReturn("{\"summary\":\"订单改善预期\",\"narrative\":{\"plainSummary\":\"新增订单可能改善收入\",\"causalSteps\":[\"订单增加\",\"收入预期改善\"]},\"drivers\":[{\"claim\":\"订单增加\",\"evidenceUrls\":[\"https://example.com/company?a=1\"]}]}");
        AttributionAssessmentService assessmentService = mock(AttributionAssessmentService.class);
        com.finscope.domain.attribution.AttributionAssessment assessment = new com.finscope.domain.attribution.AttributionAssessment();
        assessment.setStatus(status);
        when(assessmentService.research(any(), any(), any(), any(), any())).thenReturn(assessment);
        ReflectionTestUtils.setField(agent, "assessmentService", assessmentService);
        ReflectionTestUtils.setField(agent, "llmChatClient", llm);
        ArticleRepository articles = mock(ArticleRepository.class);
        when(articles.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(agent, "articleRepository", articles);
        ReflectionTestUtils.setField(agent, "agentRunRepository", mock(AgentRunRepository.class));
        Instrument instrument = new Instrument();
        instrument.setCode("NVDA");
        instrument.setName("英伟达");
        instrument.setType("STOCK");
        AttributionReport report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-18"));

        agent.research(report, instrument, 2.5D, "task-1", mock(AttributionProgressPublisher.class));

        assertEquals("新增订单可能改善收入", report.getNarrative().getPlainSummary());
        assertEquals(1, report.getDrivers().size());
        assertEquals(2, report.getNarrative().getCausalSteps().size());
        assertEquals(assessment, report.getAssessment());
    }
    @Test
    void filtersFutureWebAndLocalNewsBeforeCallingModelForHistoricalTradingDay() throws Exception {
        SearchEvidenceGateway gateway = mock(SearchEvidenceGateway.class);
        when(gateway.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        SearchEvidence current = new SearchEvidence();
        current.setTitle("目标交易日公告");
        current.setUrl("https://example.com/current");
        current.setPublishedAt("2026-09-18T09:00:00");
        current.setSourceTier("T1");
        SearchEvidence future = new SearchEvidence();
        future.setTitle("次日网页消息");
        future.setUrl("https://example.com/future");
        future.setPublishedAt("2026-09-19");
        when(gateway.search(any())).thenReturn(new SearchEvidenceBatch(
                Arrays.asList(current, future), Collections.emptyList(), false));
        SearchEvidenceContentService content = mock(SearchEvidenceContentService.class);
        when(content.acquire(any(), any(), any(), any(Boolean.class))).thenAnswer(invocation -> {
            SearchEvidence hit = invocation.getArgument(0);
            return new ResearchEvidenceAcquisitionResult(hit.getTitle(), "搜索摘要",
                    "FULL_TEXT", "html:readability", "SUCCESS", 12);
        });
        Article oldArticle = new Article();
        oldArticle.setTitle("英伟达历史公告");
        oldArticle.setUrl("https://local.com/old");
        oldArticle.setPublishedAt(LocalDateTime.parse("2026-09-14T12:00:00"));
        Article futureArticle = new Article();
        futureArticle.setTitle("英伟达次日本地消息");
        futureArticle.setUrl("https://local.com/future");
        futureArticle.setPublishedAt(LocalDateTime.parse("2026-09-19T12:00:00"));
        ArticleRepository articles = mock(ArticleRepository.class);
        when(articles.findAll()).thenReturn(Arrays.asList(futureArticle, oldArticle));
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any(), any())).thenReturn("{\"summary\":\"尚无法确认主因\",\"drivers\":[]}");
        AttributionAgent agent = new AttributionAgent();
        ReflectionTestUtils.setField(agent, "searchEvidenceGateway", gateway);
        ReflectionTestUtils.setField(agent, "searchEvidenceContentService", content);
        ReflectionTestUtils.setField(agent, "evidenceGate", new AttributionEvidenceGate());
        ReflectionTestUtils.setField(agent, "articleRepository", articles);
        ReflectionTestUtils.setField(agent, "llmChatClient", llm);
        ReflectionTestUtils.setField(agent, "agentRunRepository", mock(AgentRunRepository.class));
        Instrument instrument = new Instrument();
        instrument.setCode("NVDA");
        instrument.setName("英伟达");
        instrument.setType("FUND");
        AttributionReport report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-18"));

        agent.researchWithPlan(report, instrument, 2D, "task", mock(AttributionProgressPublisher.class),
                new AttributionResearchPlanFactory().create(instrument, 2D, report.getReportDate()));

        assertEquals(2, report.getEvidences().size());
        assertTrue(report.getEvidences().stream().filter(item -> item.getUrl().contains("/old"))
                .allMatch(item -> item.isHistoricalContext() && item.getPublishedAt().startsWith("2026-09-14")));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(any(), prompt.capture());
        assertTrue(prompt.getValue().contains("目标交易日:2026-09-18"));
        assertTrue(prompt.getValue().contains("发布时间=2026-09-18"));
        assertTrue(prompt.getValue().contains("历史背景=true"));
        assertTrue(prompt.getValue().contains("立场=COUNTER"));
        assertFalse(prompt.getValue().contains("次日"));
        assertTrue(report.getDrivers().isEmpty());
    }

}
