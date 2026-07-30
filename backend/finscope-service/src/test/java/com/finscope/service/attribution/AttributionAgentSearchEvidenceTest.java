package com.finscope.service.attribution;

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
        instrument.setType("STOCK");
        AttributionReport report = new AttributionReport();

        agent.research(report, instrument, 2.5D, "task-1", mock(AttributionProgressPublisher.class));

        assertEquals(1, report.getEvidences().size());
        assertEquals("公告显示订单和收入增长", report.getEvidences().get(0).getSnippet());
        assertEquals("T1", report.getEvidences().get(0).getSourceTier());
        ArgumentCaptor<SearchEvidenceRequest> captor = ArgumentCaptor.forClass(SearchEvidenceRequest.class);
        verify(gateway, times(3)).search(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(request -> request.getDepth() == SearchDepth.DEEP));
    }
}
