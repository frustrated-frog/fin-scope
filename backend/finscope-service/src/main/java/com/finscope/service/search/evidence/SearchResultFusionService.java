package com.finscope.service.search.evidence;

import com.finscope.domain.search.SearchResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class SearchResultFusionService {
    private final SearchUrlCanonicalizer canonicalizer;
    private final int rrfConstant;
    private final int maxPerDomain;

    public SearchResultFusionService(SearchUrlCanonicalizer canonicalizer, int rrfConstant, int maxPerDomain) {
        this.canonicalizer = canonicalizer;
        this.rrfConstant = Math.max(1, rrfConstant);
        this.maxPerDomain = Math.max(1, maxPerDomain);
    }

    public List<SearchEvidence> fuse(List<SearchResult> hits, int maxEvidence) {
        Map<String, SearchEvidence> merged = new LinkedHashMap<String, SearchEvidence>();
        if (hits != null) {
            for (SearchResult hit : hits) merge(merged, hit);
        }
        List<SearchEvidence> ranked = new ArrayList<SearchEvidence>(merged.values());
        Collections.sort(ranked, Comparator
                .comparingDouble(SearchEvidence::getFusionScore).reversed()
                .thenComparingInt(item -> tierRank(item.getSourceTier()))
                .thenComparing(item -> text(item.getUrl())));
        List<SearchEvidence> result = new ArrayList<SearchEvidence>();
        Map<String, Integer> domainCounts = new HashMap<String, Integer>();
        for (SearchEvidence item : ranked) {
            int count = domainCounts.getOrDefault(item.getSourceDomain(), 0);
            if (count >= maxPerDomain) continue;
            domainCounts.put(item.getSourceDomain(), count + 1);
            result.add(item);
            if (result.size() >= Math.max(1, maxEvidence)) break;
        }
        return result;
    }

    private void merge(Map<String, SearchEvidence> merged, SearchResult hit) {
        if (hit == null) return;
        String canonicalUrl = canonicalizer.canonicalize(hit.getUrl());
        if (canonicalUrl.isEmpty()) return;
        SearchEvidence item = merged.get(canonicalUrl);
        if (item == null) {
            item = new SearchEvidence();
            item.setUrl(canonicalUrl);
            item.setTitle(text(hit.getTitle()));
            item.setContent(text(hit.getContent()));
            item.setSourceDomain(domain(canonicalUrl));
            item.setSourceTier(normalizeTier(hit.getSourceTier()));
            item.setPublishedAt(text(hit.getPublishedAt()));
            item.setProviderScore(hit.getScore());
            merged.put(canonicalUrl, item);
        } else {
            if (text(hit.getContent()).length() > text(item.getContent()).length()) {
                item.setContent(text(hit.getContent()));
            }
            if (tierRank(hit.getSourceTier()) < tierRank(item.getSourceTier())) {
                item.setSourceTier(normalizeTier(hit.getSourceTier()));
            }
            if (hit.getScore() != null && (item.getProviderScore() == null
                    || hit.getScore() > item.getProviderScore())) item.setProviderScore(hit.getScore());
            if (text(item.getPublishedAt()).isEmpty()) item.setPublishedAt(text(hit.getPublishedAt()));
        }
        int rank = hit.getProviderRank() <= 0 ? 1 : hit.getProviderRank();
        item.setFusionScore(item.getFusionScore() + 1.0D / (rrfConstant + rank));
        TreeSet<String> providers = new TreeSet<String>(item.getProviders());
        if (!text(hit.getProviderCode()).isEmpty()) providers.add(hit.getProviderCode().trim().toUpperCase());
        item.setProviders(new ArrayList<String>(providers));
    }

    private String domain(String url) {
        try { return new URI(url).getHost().toLowerCase(); }
        catch (Exception ignored) { return ""; }
    }
    private String normalizeTier(String tier) { return tierRank(tier) == 1 ? "T1" : tierRank(tier) == 2 ? "T2" : "T3"; }
    private int tierRank(String tier) { return "T1".equalsIgnoreCase(tier) ? 1 : "T2".equalsIgnoreCase(tier) ? 2 : 3; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
