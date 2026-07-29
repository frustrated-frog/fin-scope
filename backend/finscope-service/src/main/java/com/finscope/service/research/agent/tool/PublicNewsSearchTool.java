package com.finscope.service.research.agent.tool;

import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.domain.search.SearchResult;
import com.finscope.rpc.search.WebSearchClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PublicNewsSearchTool implements ResearchAgentTool {
    private static final int MAX_RESULTS = 5;
    private static final double MIN_RELEVANCE_SCORE = 0.10D;
    private final WebSearchClient searchClient;
    private final ResearchSearchEvidenceRepository evidenceRepository;

    public PublicNewsSearchTool(WebSearchClient searchClient,
                                ResearchSearchEvidenceRepository evidenceRepository) {
        this.searchClient = searchClient;
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    public ResearchToolDescriptor descriptor() {
        ResearchToolDescriptor value = new ResearchToolDescriptor();
        value.setCode("public_news_search");
        value.setName("Tavily 公开资料搜索");
        value.setDescription("使用 Tavily 搜索公开资料，结果只写入本次研究证据域，不进入文章库");
        Map<String, String> input = new LinkedHashMap<String, String>();
        input.put("query", "2..180字符自然语言关键词");
        input.put("intent", "SUPPORT|COUNTER|PRIMARY|UPDATE");
        value.setInputSchema(input);
        value.setOutputSchema(Collections.singletonMap("observation", "本次研究新增证据与独立来源"));
        value.setTimeoutMs(15_000);
        value.setReadOnly(true);
        value.setParallelizable(false);
        value.setRiskLevel("LOW");
        value.setBudgetType("EXTERNAL_ACTION");
        return value;
    }

    @Override
    public void validate(Map<String, Object> arguments) {
        if (arguments == null || !arguments.keySet().equals(
                new HashSet<String>(Arrays.asList("query", "intent")))) {
            throw new IllegalArgumentException("公开资料搜索参数必须且只能包含 query 和 intent");
        }
        String query = text(arguments.get("query"));
        String intent = text(arguments.get("intent"));
        if (query.length() < 2 || query.length() > 180 || query.contains("://")) {
            throw new IllegalArgumentException("公开资料搜索 query 未通过安全校验");
        }
        if (!Arrays.asList("SUPPORT", "COUNTER", "PRIMARY", "UPDATE").contains(intent)) {
            throw new IllegalArgumentException("公开资料搜索 intent 未通过安全校验");
        }
    }

    @Override
    public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
        validate(arguments);
        if (!searchClient.isConfigured()) {
            return error("TERMINAL_ERROR", "TAVILY_NOT_CONFIGURED", false,
                    "Tavily 未配置，无法执行公开资料搜索");
        }
        String query = text(arguments.get("query"));
        String intent = text(arguments.get("intent"));
        try {
            Set<String> existingDomains = new HashSet<String>();
            for (ResearchSearchEvidence item : evidenceRepository.findByRunId(context.getResearchRunId())) {
                if (hasText(item.getSourceDomain())) existingDomains.add(item.getSourceDomain().toLowerCase());
            }
            List<SearchResult> hits = searchClient.search(query, MAX_RESULTS);
            List<String> refs = new ArrayList<String>();
            Set<String> seenUrls = new HashSet<String>();
            Set<String> newDomains = new HashSet<String>();
            int duplicates = 0;
            int lowRelevance = 0;
            for (SearchResult hit : hits == null ? Collections.<SearchResult>emptyList() : hits) {
                String url = text(hit.getUrl());
                if (hit.getScore() == null || hit.getScore() < MIN_RELEVANCE_SCORE) {
                    lowRelevance++;
                    continue;
                }
                if (!hasText(url) || !seenUrls.add(url)
                        || evidenceRepository.findByRunIdAndUrl(context.getResearchRunId(), url).isPresent()) {
                    duplicates++;
                    continue;
                }
                ResearchSearchEvidence saved = evidenceRepository.save(toEvidence(context, query, intent, hit));
                if (saved.getId() != null) refs.add("search-evidence:" + saved.getId());
                String domain = text(saved.getSourceDomain()).toLowerCase();
                if (hasText(domain) && !existingDomains.contains(domain)) newDomains.add(domain);
            }
            ResearchToolObservation value = new ResearchToolObservation();
            value.setEvidenceDelta(refs.size());
            value.setSourceDelta(newDomains.size());
            value.setDataRefs(refs);
            value.setStateHash("tavily:" + refs.size() + ":" + newDomains.size());
            value.setStatus(refs.isEmpty() ? "NO_PROGRESS" : "SUCCESS");
            value.setObservationSummary("Tavily 搜索完成：命中=" + (hits == null ? 0 : hits.size())
                    + "，低相关=" + lowRelevance + "，重复=" + duplicates + "，新增研究证据=" + refs.size()
                    + "，新增独立来源=" + newDomains.size());
            value.setNewInformation(refs.isEmpty()
                    ? "本次查询没有获得新的运行内证据"
                    : "搜索材料已进入本次研究证据域，未写入文章库");
            value.setRetryable(false);
            return value;
        } catch (Exception ex) {
            return error("RETRYABLE_ERROR", "TAVILY_SEARCH_FAILED", true,
                    "Tavily 搜索失败：" + safe(ex.getMessage()));
        }
    }

    private ResearchSearchEvidence toEvidence(ResearchAgentToolContext context, String query, String intent,
                                                SearchResult hit) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setResearchRunId(context.getResearchRunId());
        value.setDecisionId(context.getDecisionId());
        value.setProvider("TAVILY");
        value.setQueryText(query);
        value.setIntent(intent);
        value.setTitle(text(hit.getTitle()));
        value.setUrl(text(hit.getUrl()));
        value.setContent(text(hit.getContent()));
        value.setSourceDomain(text(hit.getSourceDomain()));
        value.setSourceTier(hasText(hit.getSourceTier()) ? hit.getSourceTier() : "T3");
        value.setRelevanceScore(hit.getScore());
        value.setPublishedAt(text(hit.getPublishedAt()));
        return value;
    }

    private ResearchToolObservation error(String status, String type, boolean retryable, String summary) {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setStatus(status);
        value.setErrorType(type);
        value.setRetryable(retryable);
        value.setObservationSummary(summary);
        value.setNewInformation("没有写入新的研究证据");
        value.setStateHash("tavily:error:" + type);
        return value;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String safe(String value) {
        if (!hasText(value)) return "未知搜索错误";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240);
    }
}
