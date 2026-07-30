package com.finscope.service.search.evidence;

import java.util.ArrayList;
import java.util.List;

public class SearchEvidence {
    private String title;
    private String url;
    private String content;
    private String sourceDomain;
    private String sourceTier;
    private String publishedAt;
    private Double providerScore;
    private double fusionScore;
    private List<String> providers = new ArrayList<String>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourceDomain() { return sourceDomain; }
    public void setSourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; }
    public String getSourceTier() { return sourceTier; }
    public void setSourceTier(String sourceTier) { this.sourceTier = sourceTier; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public Double getProviderScore() { return providerScore; }
    public void setProviderScore(Double providerScore) { this.providerScore = providerScore; }
    public double getFusionScore() { return fusionScore; }
    public void setFusionScore(double fusionScore) { this.fusionScore = fusionScore; }
    public List<String> getProviders() { return providers; }
    public void setProviders(List<String> providers) { this.providers = providers; }
}
