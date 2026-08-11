package com.finscope.service.search.evidence;

import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.search.WebSearchProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchEvidenceGatewayTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void quickUsesOnlyTavily() {
        StubProvider tavily = new StubProvider("TAVILY", false);
        StubProvider anySearch = new StubProvider("ANYSEARCH", false);
        SearchEvidenceGateway gateway = gateway(tavily, anySearch);

        SearchEvidenceBatch batch = gateway.search(request(SearchDepth.QUICK));

        assertEquals(1, tavily.calls.get());
        assertEquals(0, anySearch.calls.get());
        assertEquals(1, batch.getEvidence().size());
        assertFalse(batch.isAllProvidersFailed());
    }

    @Test
    void deepKeepsSuccessfulEvidenceWhenAnotherProviderFails() {
        StubProvider tavily = new StubProvider("TAVILY", false);
        StubProvider anySearch = new StubProvider("ANYSEARCH", true);
        StubProvider firecrawl = new StubProvider("FIRECRAWL", false);
        SearchEvidenceGateway gateway = gateway(tavily, anySearch, firecrawl);

        SearchEvidenceBatch batch = gateway.search(request(SearchDepth.DEEP));

        assertEquals(2, batch.getEvidence().size());
        assertFalse(batch.isAllProvidersFailed());
        assertEquals(3, batch.getDiagnostics().size());
        assertEquals(1, firecrawl.calls.get());
        assertTrue(batch.getDiagnostics().stream()
                .anyMatch(item -> "ANYSEARCH".equals(item.getProviderCode()) && item.isFailed()));
    }

    private SearchEvidenceGateway gateway(WebSearchProvider... providers) {
        return new SearchEvidenceGateway(Arrays.asList(providers), executor,
                new SearchResultFusionService(new SearchUrlCanonicalizer(), 60, 2));
    }

    private SearchEvidenceRequest request(SearchDepth depth) {
        return new SearchEvidenceRequest("NVIDIA news", depth, 4, 6,
                "intl", "en", 3000L);
    }

    private static class StubProvider implements WebSearchProvider {
        private final String code;
        private final boolean fail;
        private final AtomicInteger calls = new AtomicInteger();

        private StubProvider(String code, boolean fail) {
            this.code = code;
            this.fail = fail;
        }

        @Override public String providerCode() { return code; }
        @Override public boolean isConfigured() { return true; }

        @Override
        public List<SearchResult> search(WebSearchRequest request) throws Exception {
            calls.incrementAndGet();
            if (fail) throw new IllegalStateException("credential-body-must-not-escape");
            SearchResult result = new SearchResult();
            result.setProviderCode(code);
            result.setProviderRank(1);
            result.setTitle(code + " result");
            result.setUrl("https://" + code.toLowerCase() + ".example.com/a");
            result.setContent("evidence");
            result.setSourceTier("T2");
            return Collections.singletonList(result);
        }
    }
}
