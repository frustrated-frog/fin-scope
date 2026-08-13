package com.finscope.service.research.agent.tool;

import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.common.enums.research.ResearchMode;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.domain.search.SearchResult;
import com.finscope.service.research.agent.ResearchOrchestrator;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.source.FinancialSourceQueryPolicy;
import com.finscope.service.research.source.FinancialSourceSearchPlan;
import com.finscope.service.research.source.OfficialFinancialSourceRegistry;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PublicNewsSearchTool implements ResearchAgentTool {
    private static final int MAX_RESULTS = 5;
    private final SearchEvidenceGateway searchGateway;
    private final ResearchSearchEvidenceRepository evidenceRepository;
    private final SearchEvidenceContentService contentService;
    private final FinancialSourceQueryPolicy queryPolicy;
    private final OfficialFinancialSourceRegistry sourceRegistry;
    private final ResearchOrchestrator orchestrator;

    @Autowired
    public PublicNewsSearchTool(SearchEvidenceGateway searchGateway,
                                ResearchSearchEvidenceRepository evidenceRepository,
                                SearchEvidenceContentService contentService,
                                FinancialSourceQueryPolicy queryPolicy,
                                OfficialFinancialSourceRegistry sourceRegistry,
                                ResearchOrchestrator orchestrator) {
        this.searchGateway = searchGateway;
        this.evidenceRepository = evidenceRepository;
        this.contentService = contentService;
        this.queryPolicy = queryPolicy;
        this.sourceRegistry = sourceRegistry;
        this.orchestrator = orchestrator;
    }

    @Override
    public ResearchToolDescriptor descriptor() {
        ResearchToolDescriptor value = new ResearchToolDescriptor();
        value.setCode("public_news_search");
        value.setName("多源公开资料搜索");
        value.setDescription("使用多源搜索发现公开资料 URL，读取 HTML/PDF 原文并召回相关片段；结果只进入本次研究证据域");
        Map<String, String> input = new LinkedHashMap<String, String>();
        input.put("query", "2..180字符自然语言关键词");
        input.put("intent", "SUPPORT|COUNTER|PRIMARY|UPDATE");
        value.setInputSchema(input);
        value.setOutputSchema(Collections.singletonMap("observation", "本次研究新增证据与独立来源"));
        value.setTimeoutMs(45_000);
        value.setReadOnly(true);
        value.setParallelizable(true);
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
        ResearchMode mode = ResearchMode.defaultIfNull(context.getResearchMode());
        if (!isSearchConfigured(mode)) {
            return error("TERMINAL_ERROR", "WEB_SEARCH_NOT_CONFIGURED", false,
                    "未配置可用的公开资料搜索供应商");
        }
        String query = text(arguments.get("query"));
        String intent = text(arguments.get("intent"));
        try {
            Set<String> existingDomains = new HashSet<String>();
            for (ResearchSearchEvidence item : evidenceRepository.findByRunId(context.getResearchRunId())) {
                if (hasText(item.getSourceDomain())) existingDomains.add(item.getSourceDomain().toLowerCase());
            }
            AtomicInteger officialBranches = new AtomicInteger();
            AtomicInteger generalFallbacks = new AtomicInteger();
            List<ResearchOrchestrator.BranchResult> branches = orchestrator.execute(
                    context.getResearchMode(), query, intent, (branchQuery, branchIntent) -> {
                        FinancialSourceSearchPlan searchPlan = queryPolicy.plan(branchQuery, branchIntent);
                        if (searchPlan.isOfficialLane()) officialBranches.incrementAndGet();
                        List<SearchResult> branchHits = search(searchPlan.getEffectiveQuery(), mode);
                        if (searchPlan.isOfficialLane() && (branchHits == null || branchHits.isEmpty())) {
                            branchHits = search(searchPlan.getOriginalQuery(), mode);
                            generalFallbacks.incrementAndGet();
                        }
                        return branchHits;
                    });
            List<String> refs = new ArrayList<String>();
            Set<String> seenUrls = new HashSet<String>();
            Set<String> newDomains = new HashSet<String>();
            int duplicates = 0;
            int fullText = 0;
            int snippetFallback = 0;
            int hitCount = 0;
            int failedBranches = 0;
            int fullTextAttempts = 0;
            int fullTextBudget = context.getResearchMode().getFullTextReadsPerSearch();
            for (ResearchOrchestrator.BranchResult branch : branches) {
                if (!branch.isSuccess()) {
                    failedBranches++;
                    continue;
                }
                hitCount += branch.getHits().size();
                for (SearchResult hit : branch.getHits()) {
                    String url = text(hit.getUrl());
                    if (!hasText(url) || !seenUrls.add(url)
                            || evidenceRepository.findByRunIdAndUrl(context.getResearchRunId(), url).isPresent()) {
                        duplicates++;
                        continue;
                    }
                    boolean readFullText = fullTextAttempts < fullTextBudget;
                    if (readFullText) fullTextAttempts++;
                    ResearchSearchEvidence candidate = toEvidence(context, branch.getQuery(), branch.getIntent(),
                            hit, readFullText);
                    if ("FULL_TEXT".equals(candidate.getContentOrigin())) fullText++; else snippetFallback++;
                    ResearchSearchEvidence saved = evidenceRepository.save(candidate);
                    if (saved.getId() != null) refs.add("search-evidence:" + saved.getId());
                    String domain = text(saved.getSourceDomain()).toLowerCase();
                    if (hasText(domain) && !existingDomains.contains(domain)) newDomains.add(domain);
                }
            }
            if (failedBranches == branches.size()) {
                return error("RETRYABLE_ERROR", "WEB_SEARCH_FAILED", true,
                        "全部研究搜索分支均执行失败");
            }
            ResearchToolObservation value = new ResearchToolObservation();
            value.setEvidenceDelta(refs.size());
            value.setSourceDelta(newDomains.size());
            value.setDataRefs(refs);
            value.setStateHash("web-search:" + refs.size() + ":" + newDomains.size());
            value.setStatus(refs.isEmpty() ? "NO_PROGRESS" : "SUCCESS");
            value.setObservationSummary("多源公开资料搜索完成：命中=" + hitCount
                    + "，研究分支=" + branches.size() + "，失败分支=" + failedBranches
                    + "，官方通道=" + (officialBranches.get() > 0) + "，通用降级=" + (generalFallbacks.get() > 0)
                    + "，重复=" + duplicates + "，新增研究证据=" + refs.size()
                    + "，全文=" + fullText + "，摘要降级=" + snippetFallback
                    + "，全文读取预算=" + fullTextBudget
                    + "，新增独立来源=" + newDomains.size());
            value.setNewInformation(refs.isEmpty()
                    ? "本次查询没有获得新的运行内证据"
                    : "搜索材料已进入本次研究证据域，未写入文章库");
            value.setRetryable(false);
            return value;
        } catch (Exception ex) {
            return error("RETRYABLE_ERROR", "WEB_SEARCH_FAILED", true,
                    "公开资料搜索失败：" + safe(ex.getMessage()));
        }
    }

    private ResearchSearchEvidence toEvidence(ResearchAgentToolContext context, String query, String intent,
                                                SearchResult hit, boolean readFullText) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setResearchRunId(context.getResearchRunId());
        value.setDecisionId(context.getDecisionId());
        value.setProvider(hasText(hit.getProviderCode()) ? hit.getProviderCode() : "TAVILY");
        value.setQueryText(query);
        value.setIntent(intent);
        value.setTitle(text(hit.getTitle()));
        value.setUrl(text(hit.getUrl()));
        SearchEvidence searchEvidence = new SearchEvidence();
        searchEvidence.setUrl(hit.getUrl());
        searchEvidence.setContent(hit.getContent());
        ResearchEvidenceAcquisitionResult acquired = contentService.acquire(searchEvidence, query, query,
                readFullText);
        value.setContent(acquired.getContent());
        value.setSearchSnippet(acquired.getSearchSnippet());
        value.setContentOrigin(acquired.getContentOrigin());
        value.setExtractionMethod(acquired.getExtractionMethod());
        value.setFetchStatus(acquired.getFetchStatus());
        value.setContentCharCount(acquired.getContentCharCount());
        value.setFetchedAt(java.time.LocalDateTime.now());
        value.setSourceDomain(text(hit.getSourceDomain()));
        value.setSourceTier(sourceRegistry.resolveTier(value.getSourceDomain(), hit.getSourceTier()));
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
        value.setStateHash("web-search:error:" + type);
        return value;
    }

    private boolean isSearchConfigured(ResearchMode mode) {
        return searchGateway != null && searchGateway.isConfigured(toDepth(mode));
    }

    private List<SearchResult> search(String query, ResearchMode mode) throws Exception {
        boolean chinese = query != null && query.matches(".*[\\u4e00-\\u9fa5].*");
        SearchEvidenceBatch batch = searchGateway.search(new SearchEvidenceRequest(query, toDepth(mode),
                MAX_RESULTS, MAX_RESULTS, chinese ? "cn" : "intl", chinese ? "zh" : "en", 15_000L));
        if (batch.isAllProvidersFailed()) throw new IllegalStateException("所有搜索供应商均不可用");
        List<SearchResult> results = new ArrayList<SearchResult>();
        for (SearchEvidence evidence : batch.getEvidence()) {
            SearchResult result = new SearchResult();
            result.setTitle(evidence.getTitle());
            result.setUrl(evidence.getUrl());
            result.setContent(evidence.getContent());
            result.setSourceDomain(evidence.getSourceDomain());
            result.setSourceTier(evidence.getSourceTier());
            result.setPublishedAt(evidence.getPublishedAt());
            result.setScore(evidence.getProviderScore());
            result.setProviderCode(String.join("+", evidence.getProviders()));
            results.add(result);
        }
        return results;
    }

    private SearchDepth toDepth(ResearchMode mode) {
        return mode == ResearchMode.QUICK ? SearchDepth.QUICK : SearchDepth.DEEP;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String safe(String value) {
        if (!hasText(value)) return "未知搜索错误";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240);
    }
}
