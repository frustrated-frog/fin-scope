package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import com.finscope.service.search.evidence.SearchUrlCanonicalizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndustryChainEvidenceCollectorTest {

    @Test
    void runsBoundedDeepQueriesDeduplicatesUrlsAndReadsOnlyFirstThreeDocuments() {
        SearchEvidenceGateway search = mock(SearchEvidenceGateway.class);
        SearchEvidenceContentService content = mock(SearchEvidenceContentService.class);
        when(search.search(any(SearchEvidenceRequest.class))).thenAnswer(invocation -> {
            SearchEvidenceRequest request = invocation.getArgument(0);
            int call = request.getQuery().contains("全景") ? 1
                    : request.getQuery().contains("上游") ? 2
                    : request.getQuery().contains("核心") ? 3
                    : request.getQuery().contains("下游") ? 4 : 5;
            return new SearchEvidenceBatch(Arrays.asList(
                    hit("https://example.com/report?utm_source=test", "报告"),
                    hit("https://source" + call + ".cn/item", "资料" + call)),
                    Collections.emptyList(), false);
        });
        when(content.acquire(any(SearchEvidence.class), anyString(), anyString(), any(Boolean.class)))
                .thenAnswer(invocation -> {
                    SearchEvidence hit = invocation.getArgument(0);
                    boolean full = invocation.getArgument(3);
                    return new ResearchEvidenceAcquisitionResult(
                            full ? "完整正文" : hit.getContent(), hit.getContent(),
                            full ? "WEB" : "SEARCH_SNIPPET", "test", "OK", 4);
                });
        IndustryChainEvidenceCollector collector = new IndustryChainEvidenceCollector(
                search, content, new SearchUrlCanonicalizer());

        List<IndustryChainEvidence> evidence = collector.collect("AI算力");

        assertEquals(6, evidence.size());
        assertEquals("E1", evidence.get(0).getEvidenceCode());
        assertEquals("E6", evidence.get(5).getEvidenceCode());
        assertEquals(6, evidence.stream().map(IndustryChainEvidence::getUrl).distinct().count());
        ArgumentCaptor<Boolean> fullText = ArgumentCaptor.forClass(Boolean.class);
        verify(content, times(6)).acquire(any(SearchEvidence.class), anyString(), anyString(), fullText.capture());
        assertEquals(Arrays.asList(true, true, true, false, false, false), fullText.getAllValues());
        ArgumentCaptor<SearchEvidenceRequest> requests = ArgumentCaptor.forClass(SearchEvidenceRequest.class);
        verify(search, times(5)).search(requests.capture());
        assertTrue(requests.getAllValues().stream().allMatch(
                request -> "DEEP".equals(request.getDepth().name())));
    }

    private SearchEvidence hit(String url, String title) {
        SearchEvidence value = new SearchEvidence();
        value.setUrl(url);
        value.setTitle(title);
        value.setContent(title + "摘要");
        value.setSourceDomain("example.com");
        value.setSourceTier("T2");
        value.setProviders(Collections.singletonList("TAVILY"));
        return value;
    }
}
