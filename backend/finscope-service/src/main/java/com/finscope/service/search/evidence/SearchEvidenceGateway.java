package com.finscope.service.search.evidence;

import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.search.WebSearchProvider;
import com.finscope.rpc.search.WebSearchProviderException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SearchEvidenceGateway {
    private final List<WebSearchProvider> providers;
    private final ExecutorService executor;
    private final SearchResultFusionService fusionService;

    public SearchEvidenceGateway(List<WebSearchProvider> providers, ExecutorService executor,
                                 SearchResultFusionService fusionService) {
        this.providers = providers == null ? Collections.<WebSearchProvider>emptyList() : providers;
        this.executor = executor;
        this.fusionService = fusionService;
    }

    public boolean isConfigured(SearchDepth depth) {
        for (WebSearchProvider provider : selected(depth)) if (provider.isConfigured()) return true;
        return false;
    }

    public SearchEvidenceBatch search(SearchEvidenceRequest request) {
        List<WebSearchProvider> selected = selected(request.getDepth());
        List<ProviderCall> calls = new ArrayList<ProviderCall>();
        long started = System.nanoTime();
        for (WebSearchProvider provider : selected) {
            if (!provider.isConfigured()) continue;
            long callStarted = System.nanoTime();
            Future<List<SearchResult>> future = executor.submit(() -> provider.search(new WebSearchRequest(
                    request.getQuery(), request.getMaxResultsPerProvider(), request.getZone(), request.getLanguage())));
            calls.add(new ProviderCall(provider, future, callStarted));
        }
        List<SearchResult> hits = new ArrayList<SearchResult>();
        List<SearchProviderDiagnostic> diagnostics = new ArrayList<SearchProviderDiagnostic>();
        int successCount = 0;
        for (ProviderCall call : calls) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            long remainingMs = Math.max(1L, request.getTimeoutMs() - elapsedMs);
            try {
                List<SearchResult> providerHits = call.future.get(remainingMs, TimeUnit.MILLISECONDS);
                if (providerHits != null) hits.addAll(providerHits);
                successCount++;
                diagnostics.add(new SearchProviderDiagnostic(call.provider.providerCode(), latency(call),
                        providerHits == null ? 0 : providerHits.size(), false, ""));
            } catch (Exception ex) {
                call.future.cancel(true);
                diagnostics.add(new SearchProviderDiagnostic(call.provider.providerCode(), latency(call),
                        0, true, errorType(ex)));
            }
        }
        Collections.sort(diagnostics, Comparator.comparing(SearchProviderDiagnostic::getProviderCode));
        return new SearchEvidenceBatch(fusionService.fuse(hits, request.getMaxEvidence()), diagnostics,
                calls.isEmpty() || successCount == 0);
    }

    private List<WebSearchProvider> selected(SearchDepth depth) {
        List<WebSearchProvider> result = new ArrayList<WebSearchProvider>();
        for (WebSearchProvider provider : providers) {
            String code = provider.providerCode() == null ? "" : provider.providerCode().toUpperCase(Locale.ROOT);
            if (depth == SearchDepth.QUICK && !"TAVILY".equals(code)) continue;
            if ("TAVILY".equals(code) || "ANYSEARCH".equals(code) || "FIRECRAWL".equals(code)) {
                result.add(provider);
            }
        }
        Collections.sort(result, Comparator.comparing(WebSearchProvider::providerCode));
        return result;
    }

    private String errorType(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        if (cause instanceof WebSearchProviderException) {
            int status = ((WebSearchProviderException) cause).getStatusCode();
            return status > 0 ? "HTTP_" + status : "PROVIDER_ERROR";
        }
        if (cause instanceof java.util.concurrent.TimeoutException
                || exception instanceof java.util.concurrent.TimeoutException) return "TIMEOUT";
        return "PROVIDER_ERROR";
    }

    private long latency(ProviderCall call) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - call.startedAtNanos);
    }

    private static class ProviderCall {
        private final WebSearchProvider provider;
        private final Future<List<SearchResult>> future;
        private final long startedAtNanos;
        private ProviderCall(WebSearchProvider provider, Future<List<SearchResult>> future, long startedAtNanos) {
            this.provider = provider;
            this.future = future;
            this.startedAtNanos = startedAtNanos;
        }
    }
}
