package com.finscope.service.research.agent.tool;

import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.search.SearchResult;
import com.finscope.rpc.search.WebSearchClient;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionService;
import com.finscope.service.research.source.FinancialSourceQueryPolicy;
import com.finscope.service.research.source.OfficialFinancialSourceRegistry;
import com.finscope.service.research.agent.BoundedResearchOrchestrator;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
        assertTrue(observation.getObservationSummary().contains("多源公开资料搜索"));
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

        ResearchToolObservation noProgress = tool.execute(
                new ResearchAgentToolContext(22L, 9L, ResearchMode.QUICK), arguments());
        ResearchToolObservation failed = tool.execute(
                new ResearchAgentToolContext(22L, 10L, ResearchMode.QUICK), arguments());

        assertEquals("NO_PROGRESS", noProgress.getStatus());
        assertEquals("RETRYABLE_ERROR", failed.getStatus());
        assertEquals("WEB_SEARCH_FAILED", failed.getErrorType());
        assertTrue(failed.isRetryable());
    }

    @Test
    void retainsProviderResultsEvenWhenRawScoresAreLowOrMissing() throws Exception {
        when(searchClient.isConfigured()).thenReturn(true);
        SearchResult relevant = result("China memory chipmaker CXMT completes IPO",
                "https://www.cnbc.com/cxmt", "The company completed its Shanghai listing.", "T2");
        relevant.setScore(0.478D);
        SearchResult noise = result("Aspinall returns to training",
                "https://sport.example.com/a", "The fighter recovered from eye surgery.", "T3");
        noise.setScore(null);
        when(searchClient.search("光模块 指引 下修 风险", 5)).thenReturn(Arrays.asList(relevant, noise));
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId(41L);
            return value;
        });

        ResearchToolObservation observation = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments());

        assertEquals(2, observation.getEvidenceDelta());
        verify(evidenceRepository, times(2)).save(any(ResearchSearchEvidence.class));
    }

    @Test
    void primaryIntentSearchesOfficialLaneAndOverridesTierFromRegistry() throws Exception {
        OfficialFinancialSourceRegistry registry = new OfficialFinancialSourceRegistry();
        FinancialSourceQueryPolicy queryPolicy = new FinancialSourceQueryPolicy(registry);
        tool = new PublicNewsSearchTool(searchClient, evidenceRepository, acquisitionService, queryPolicy, registry);
        when(searchClient.isConfigured()).thenReturn(true);
        SearchResult official = result("上市公告", "https://static.sse.com.cn/disclosure/a.pdf",
                "募集资金用于先进制程研发", "T3");
        String officialQuery = queryPolicy.plan("长鑫科技 IPO 募集资金", "PRIMARY").getEffectiveQuery();
        when(searchClient.search(officialQuery, 5)).thenReturn(Collections.singletonList(official));
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId(88L);
            return value;
        });
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("query", "长鑫科技 IPO 募集资金");
        arguments.put("intent", "PRIMARY");

        ResearchToolObservation observation = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments);

        ArgumentCaptor<ResearchSearchEvidence> captor = ArgumentCaptor.forClass(ResearchSearchEvidence.class);
        verify(evidenceRepository).save(captor.capture());
        assertEquals("T1", captor.getValue().getSourceTier());
        assertTrue(observation.getObservationSummary().contains("官方通道=true"));
        verify(searchClient).search(officialQuery, 5);
    }

    @Test
    void deepUsesBoundedBranchesButCommitsEvidenceSequentiallyOnCallerThread() throws Exception {
        when(searchClient.isConfigured()).thenReturn(true);
        when(searchClient.search(any(String.class), eq(5))).thenAnswer(invocation -> Collections.singletonList(
                result("分支材料", "https://" + Math.abs(invocation.getArgument(0).hashCode()) + ".example.com/a",
                        "分支证据", "T2")));
        String callerThread = Thread.currentThread().getName();
        CopyOnWriteArrayList<String> commitThreads = new CopyOnWriteArrayList<String>();
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            commitThreads.add(Thread.currentThread().getName());
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId((long) commitThreads.size());
            return value;
        });

        ResearchToolObservation observation = tool.execute(
                new ResearchAgentToolContext(22L, 9L, ResearchMode.DEEP), arguments());

        assertEquals(3, observation.getEvidenceDelta());
        assertTrue(observation.getObservationSummary().contains("研究分支=3"));
        assertEquals(Arrays.asList(callerThread, callerThread, callerThread), commitThreads);
        verify(acquisitionService, times(3)).acquire(any(String.class), any(String.class),
                any(String.class), any(String.class));
    }

    @Test
    void quickUsesOneBranchAndLimitsFullTextReadsToTwo() throws Exception {
        when(searchClient.isConfigured()).thenReturn(true);
        when(searchClient.search("光模块 指引 下修 风险", 5)).thenReturn(Arrays.asList(
                result("材料一", "https://a.example.com/1", "一", "T2"),
                result("材料二", "https://b.example.com/2", "二", "T2"),
                result("材料三", "https://c.example.com/3", "三", "T2")));
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId((long) value.getUrl().hashCode());
            return value;
        });

        ResearchToolObservation observation = tool.execute(
                new ResearchAgentToolContext(22L, 9L, ResearchMode.QUICK), arguments());

        assertEquals(3, observation.getEvidenceDelta());
        assertTrue(observation.getObservationSummary().contains("研究分支=1"));
        assertTrue(observation.getObservationSummary().contains("全文读取预算=2"));
        verify(acquisitionService, times(2)).acquire(any(String.class), any(String.class),
                any(String.class), any(String.class));
    }

    @Test
    void consumesSharedGatewayAndPersistsAllContributingProviders() {
        SearchEvidenceGateway gateway = mock(SearchEvidenceGateway.class);
        when(gateway.isConfigured(SearchDepth.QUICK)).thenReturn(true);
        SearchEvidence evidence = new SearchEvidence();
        evidence.setTitle("共同命中的公告");
        evidence.setUrl("https://official.example.com/a");
        evidence.setContent("公告正文摘要");
        evidence.setSourceDomain("official.example.com");
        evidence.setSourceTier("T1");
        evidence.setProviders(Arrays.asList("ANYSEARCH", "TAVILY"));
        when(gateway.search(any())).thenReturn(new SearchEvidenceBatch(
                Collections.singletonList(evidence), Collections.emptyList(), false));
        when(evidenceRepository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0);
            value.setId(99L);
            return value;
        });
        OfficialFinancialSourceRegistry registry = new OfficialFinancialSourceRegistry();
        tool = new PublicNewsSearchTool(gateway, evidenceRepository, acquisitionService,
                new FinancialSourceQueryPolicy(registry), registry, new BoundedResearchOrchestrator());

        ResearchToolObservation observation = tool.execute(
                new ResearchAgentToolContext(22L, 9L, ResearchMode.QUICK), arguments());

        ArgumentCaptor<ResearchSearchEvidence> captor = ArgumentCaptor.forClass(ResearchSearchEvidence.class);
        verify(evidenceRepository).save(captor.capture());
        assertEquals("ANYSEARCH+TAVILY", captor.getValue().getProvider());
        assertEquals(1, observation.getEvidenceDelta());
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
        value.setProviderCode("TAVILY");
        return value;
    }
}
