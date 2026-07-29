package com.finscope.service.research.agent.tool;

import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.search.SearchResult;
import com.finscope.rpc.search.WebSearchClient;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PublicNewsSearchToolTest {
    private WebSearchClient searchClient;
    private ResearchSearchEvidenceRepository evidenceRepository;
    private ResearchEvidenceAcquisitionService acquisitionService;
    private PublicNewsSearchTool tool;

    @BeforeEach
    void setUp() {
        searchClient = mock(WebSearchClient.class);
        evidenceRepository = mock(ResearchSearchEvidenceRepository.class);
        acquisitionService = mock(ResearchEvidenceAcquisitionService.class);
        when(acquisitionService.acquire(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenAnswer(invocation -> new ResearchEvidenceAcquisitionResult(
                        "[S2] 原文片段：" + invocation.getArgument(2), invocation.getArgument(2), "FULL_TEXT",
                        "web:generic-score", "FETCHED", 1680));
        tool = new PublicNewsSearchTool(searchClient, evidenceRepository, acquisitionService);
    }

    @Test
    void persistsTavilyHitsOnlyAsRunScopedResearchEvidence() throws Exception {
        when(searchClient.isConfigured()).thenReturn(true);
        when(searchClient.search("光模块 指引 下修 风险", 5)).thenReturn(Arrays.asList(
                result("供应商下调全年指引", "https://news.example.com/a", "需求增速低于预期", "T2"),
                result("供应商下调全年指引", "https://news.example.com/a", "重复结果", "T2"),
                result("行业库存回升", "https://industry.example.com/b", "库存出现回升", "T1")));
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId(value.getUrl().endsWith("/a") ? 31L : 32L);
            return value;
        });

        ResearchToolObservation observation = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments());

        assertEquals("SUCCESS", observation.getStatus());
        assertEquals(2, observation.getEvidenceDelta());
        assertEquals(2, observation.getSourceDelta());
        assertEquals(Arrays.asList("search-evidence:31", "search-evidence:32"), observation.getDataRefs());
        assertTrue(observation.getObservationSummary().contains("Tavily"));
        verify(evidenceRepository, times(2)).save(any(ResearchSearchEvidence.class));
        verify(searchClient).search(eq("光模块 指引 下修 风险"), eq(5));
        assertFalse(observation.isRetryable());
        ArgumentCaptor<ResearchSearchEvidence> captor = ArgumentCaptor.forClass(ResearchSearchEvidence.class);
        verify(evidenceRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(value -> "FULL_TEXT".equals(value.getContentOrigin())));
        assertTrue(captor.getAllValues().stream().allMatch(value -> value.getContent().startsWith("[S2]")));
        assertTrue(observation.getObservationSummary().contains("全文=2"));
    }

    @Test
    void returnsNoProgressForEmptySearchAndRetryableErrorForProviderFailure() throws Exception {
        when(searchClient.isConfigured()).thenReturn(true);
        when(searchClient.search("光模块 指引 下修 风险", 5))
                .thenReturn(Collections.<SearchResult>emptyList())
                .thenThrow(new IllegalStateException("provider timeout"));

        ResearchToolObservation noProgress = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments());
        ResearchToolObservation failed = tool.execute(new ResearchAgentToolContext(22L, 10L), arguments());

        assertEquals("NO_PROGRESS", noProgress.getStatus());
        assertEquals("RETRYABLE_ERROR", failed.getStatus());
        assertEquals("TAVILY_SEARCH_FAILED", failed.getErrorType());
        assertTrue(failed.isRetryable());
    }

    @Test
    void discardsLowConfidenceProviderNoiseBeforePersistence() throws Exception {
        when(searchClient.isConfigured()).thenReturn(true);
        SearchResult relevant = result("China memory chipmaker CXMT completes IPO",
                "https://www.cnbc.com/cxmt", "The company completed its Shanghai listing.", "T2");
        relevant.setScore(0.478D);
        SearchResult noise = result("Aspinall returns to training",
                "https://sport.example.com/a", "The fighter recovered from eye surgery.", "T3");
        noise.setScore(0.080D);
        when(searchClient.search("光模块 指引 下修 风险", 5)).thenReturn(Arrays.asList(relevant, noise));
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId(41L);
            return value;
        });

        ResearchToolObservation observation = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments());

        assertEquals(1, observation.getEvidenceDelta());
        assertTrue(observation.getObservationSummary().contains("低相关=1"));
        verify(evidenceRepository, times(1)).save(any(ResearchSearchEvidence.class));
    }

    private Map<String, Object> arguments() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("query", "光模块 指引 下修 风险");
        value.put("intent", "COUNTER");
        return value;
    }

    private SearchResult result(String title, String url, String content, String tier) throws Exception {
        SearchResult value = new SearchResult();
        value.setTitle(title);
        value.setUrl(url);
        value.setContent(content);
        value.setSourceDomain(new java.net.URI(url).getHost());
        value.setSourceTier(tier);
        value.setScore(0.91D);
        value.setPublishedAt("2026-07-29");
        return value;
    }
}
