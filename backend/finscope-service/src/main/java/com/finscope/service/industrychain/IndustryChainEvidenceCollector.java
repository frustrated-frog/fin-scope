package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import com.finscope.service.search.evidence.SearchUrlCanonicalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过多个有界问题冻结产业链图谱的公开证据包。 */
@Service
public class IndustryChainEvidenceCollector {
    private static final int MAX_EVIDENCE = 12;
    private static final int MAX_CONTENT = 6000;
    private static final List<String> QUERY_DIMENSIONS = Arrays.asList(
            "产业链全景 价值链 主要环节 行业报告",
            "产业链上游 原材料 核心零部件 供应格局",
            "产业链核心 制造 产品 技术路线 竞争格局",
            "产业链下游 应用场景 客户行业 需求",
            "A股 代表公司 主营业务 年报 公告");

    private final SearchEvidenceGateway search;
    private final SearchEvidenceContentService content;
    private final SearchUrlCanonicalizer canonicalizer;

    public IndustryChainEvidenceCollector(SearchEvidenceGateway search,
                                          SearchEvidenceContentService content,
                                          SearchUrlCanonicalizer canonicalizer) {
        this.search = search;
        this.content = content;
        this.canonicalizer = canonicalizer;
    }

    public List<IndustryChainEvidence> collect(String chainName) {
        String subject = text(chainName);
        Map<String, SearchHit> unique = new LinkedHashMap<String, SearchHit>();
        for (String dimension : QUERY_DIMENSIONS) {
            String query = subject + " " + dimension;
            SearchEvidenceBatch batch = search.search(new SearchEvidenceRequest(
                    query, SearchDepth.DEEP, 5, 6, "cn", "zh", 25_000));
            for (SearchEvidence hit : batch.getEvidence()) {
                String canonicalUrl = canonicalizer.canonicalize(text(hit.getUrl()));
                if (!canonicalUrl.isEmpty() && !unique.containsKey(canonicalUrl)
                        && unique.size() < MAX_EVIDENCE) {
                    unique.put(canonicalUrl, new SearchHit(hit, query, canonicalUrl));
                }
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalStateException("产业链公开资料搜索暂不可用");
        }
        List<IndustryChainEvidence> result = new ArrayList<IndustryChainEvidence>();
        int index = 1;
        for (SearchHit searchHit : unique.values()) {
            SearchEvidence hit = searchHit.evidence;
            ResearchEvidenceAcquisitionResult acquired = content.acquire(
                    hit, searchHit.query, subject, index <= 3);
            IndustryChainEvidence evidence = new IndustryChainEvidence();
            evidence.setEvidenceCode("E" + index);
            evidence.setTitle(text(hit.getTitle()));
            evidence.setUrl(searchHit.canonicalUrl);
            evidence.setSource(text(hit.getSourceDomain()));
            evidence.setSourceTier(text(hit.getSourceTier()));
            evidence.setPublishedAt(text(hit.getPublishedAt()));
            evidence.setExcerpt(limit(text(acquired.getContent()), MAX_CONTENT));
            result.add(evidence);
            index++;
        }
        return result;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SearchHit {
        private final SearchEvidence evidence;
        private final String query;
        private final String canonicalUrl;

        private SearchHit(SearchEvidence evidence, String query, String canonicalUrl) {
            this.evidence = evidence;
            this.query = query;
            this.canonicalUrl = canonicalUrl;
        }
    }
}
