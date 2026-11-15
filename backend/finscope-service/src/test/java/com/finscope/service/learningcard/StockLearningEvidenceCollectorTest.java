package com.finscope.service.learningcard;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockLearningEvidenceCollectorTest {
    private StockLearningEvidenceCollector collector;
    private SearchEvidenceGateway search;
    private SearchEvidenceContentService content;
    private Instrument stock;

    @BeforeEach
    void setUp() {
        collector = new StockLearningEvidenceCollector();
        search = mock(SearchEvidenceGateway.class);
        content = mock(SearchEvidenceContentService.class);
        ReflectionTestUtils.setField(collector, "search", search);
        ReflectionTestUtils.setField(collector, "content", content);
        stock = new Instrument();
        stock.setId(1L);
        stock.setCode("605058");
        stock.setName("澳弘电子");
    }

    @Test
    void rejectsOtherCompaniesAndNearMatchesBeforeDownloading() {
        when(search.search(any())).thenReturn(batch(List.of(
                hit("盐湖股份投资者交流", "https://example.com/000792", "盐湖股份收入与现金流"),
                hit("澳光电子合同纠纷", "https://example.com/case", "澳光电子制造有限公司"),
                hit("其他股票", "https://example.com/16050580", "没有目标公司"),
                hit("其他公司金额", "https://example.com/amount", "收到营业款605058元"),
                hit("澳弘电子资料", "javascript:void(0)", "澳弘电子收入资料"))));

        assertTrue(collector.collect(stock, "PROFIT_MODEL", List.of()).isEmpty());
        verifyNoInteractions(content);
        org.mockito.ArgumentCaptor<com.finscope.service.search.evidence.SearchEvidenceRequest> request =
                org.mockito.ArgumentCaptor.forClass(com.finscope.service.search.evidence.SearchEvidenceRequest.class);
        verify(search).search(request.capture());
        assertTrue(request.getValue().getQuery().contains("\"澳弘电子\" \"605058\""));
    }

    @Test
    void keepsVerifiedBaselineOnSearchFailureAndDoesNotShareMutableEvidenceBetweenDimensions() {
        var seed = new StockLearningCardEvidence("", "澳弘电子公司资料", "https://example.com/605058", "公司资料", "", "澳弘电子主营印制电路板");
        seed.setContentOrigin("COMPANY_PROFILE");
        when(search.search(any())).thenThrow(new IllegalStateException("search down"));
        var first = collector.collect(stock, "SPACE", List.of(seed));
        first.get(0).setDimensionCode("SPACE");
        var second = collector.collect(stock, "PROFIT_MODEL", List.of(seed));

        assertEquals("E1", second.get(0).getEvidenceCode());
        assertNull(second.get(0).getDimensionCode());
        assertNull(seed.getDimensionCode());
        assertNotSame(first.get(0), second.get(0));
    }

    @Test
    void doesNotReplaceTargetCompanySnippetWithAnUnrelatedFetchedBody() {
        when(search.search(any())).thenReturn(batch(List.of(hit("澳弘电子年报", "https://example.com/605058", "澳弘电子主营印制电路板"))));
        when(content.acquire(any(), anyString(), anyString(), anyBoolean())).thenReturn(
                new ResearchEvidenceAcquisitionResult("盐湖股份主营钾肥", "", "FULL_TEXT", "test", "SUCCESS", 9));

        var results = collector.collect(stock, "PROFIT_MODEL", List.of());
        assertEquals(1, results.size());
        assertEquals("澳弘电子主营印制电路板", results.get(0).content());
        assertEquals("SEARCH_SNIPPET", results.get(0).getContentOrigin());
    }

    @Test
    void aSingleUnreadablePageDoesNotLoseOtherEvidence() {
        var first = hit("澳弘电子公司公告", "https://example.com/a", "澳弘电子收入资料");
        var second = hit("澳弘电子财报", "https://example.com/b", "澳弘电子现金流资料");
        when(search.search(any())).thenReturn(batch(List.of(first, second)));
        when(content.acquire(any(), anyString(), anyString(), anyBoolean())).thenThrow(new IllegalStateException("page unavailable"));
        var result = collector.collect(stock, "PROFIT_MODEL", List.of());
        assertEquals(2, result.size());
        assertEquals(List.of("E1", "E2"), result.stream().map(StockLearningCardEvidence::getEvidenceCode).toList());
    }

    private SearchEvidence hit(String title, String url, String snippet) {
        var result = new SearchEvidence();
        result.setTitle(title);
        result.setUrl(url);
        result.setContent(snippet);
        return result;
    }

    private SearchEvidenceBatch batch(List<SearchEvidence> hits) {
        return new SearchEvidenceBatch(hits, List.of(), false);
    }
}
