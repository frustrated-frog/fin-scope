package com.finscope.service.search.evidence;

import com.finscope.domain.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchResultFusionServiceTest {
    private final SearchUrlCanonicalizer canonicalizer = new SearchUrlCanonicalizer();
    private final SearchResultFusionService fusion = new SearchResultFusionService(canonicalizer, 60, 2);

    @Test
    void canonicalizesTrackingUrlsAndMergesContributingProviders() {
        SearchResult tavily = hit("TAVILY", 2,
                "https://EXAMPLE.com:443/news?utm_source=x&id=7#part", 0.82D, "T2");
        SearchResult anySearch = hit("ANYSEARCH", 1,
                "https://example.com/news?id=7", null, "T3");

        List<SearchEvidence> result = fusion.fuse(Arrays.asList(tavily, anySearch), 5);

        assertEquals("https://example.com/news?id=7", canonicalizer.canonicalize(tavily.getUrl()));
        assertEquals(1, result.size());
        assertEquals(Arrays.asList("ANYSEARCH", "TAVILY"), result.get(0).getProviders());
        assertEquals("T2", result.get(0).getSourceTier());
        assertEquals(0.82D, result.get(0).getProviderScore(), 0.001D);
    }

    @Test
    void retainsScorelessResultsAndCapsSingleDomain() {
        SearchResult first = hit("ANYSEARCH", 1, "https://example.com/a", null, "T3");
        SearchResult second = hit("ANYSEARCH", 2, "https://example.com/b", null, "T3");
        SearchResult third = hit("ANYSEARCH", 3, "https://example.com/c", null, "T3");
        SearchResult other = hit("TAVILY", 1, "https://official.gov.cn/a", 0.9D, "T1");

        List<SearchEvidence> result = fusion.fuse(Arrays.asList(first, second, third, other), 5);

        assertEquals(3, result.size());
        assertEquals(2, result.stream().filter(item -> "example.com".equals(item.getSourceDomain())).count());
        assertNull(result.stream().filter(item -> "example.com".equals(item.getSourceDomain()))
                .findFirst().get().getProviderScore());
    }

    private SearchResult hit(String provider, int rank, String url, Double score, String tier) {
        SearchResult result = new SearchResult();
        result.setProviderCode(provider);
        result.setProviderRank(rank);
        result.setTitle(provider + " title " + rank);
        result.setUrl(url);
        result.setContent(provider + " content " + rank);
        result.setSourceTier(tier);
        result.setScore(score);
        return result;
    }
}
