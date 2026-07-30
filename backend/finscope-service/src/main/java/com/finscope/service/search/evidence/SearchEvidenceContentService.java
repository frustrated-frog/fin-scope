package com.finscope.service.search.evidence;

import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionService;
import org.springframework.stereotype.Service;

/**
 * 搜索命中后的统一内容获取入口，供研究与归因流程共同复用。
 */
@Service
public class SearchEvidenceContentService {
    private final ResearchEvidenceAcquisitionService acquisitionService;

    public SearchEvidenceContentService(ResearchEvidenceAcquisitionService acquisitionService) {
        this.acquisitionService = acquisitionService;
    }

    public ResearchEvidenceAcquisitionResult acquire(SearchEvidence evidence, String query,
                                                     String subject, boolean readFullText) {
        String snippet = text(evidence == null ? null : evidence.getContent());
        if (!readFullText || evidence == null) {
            return new ResearchEvidenceAcquisitionResult(snippet, snippet, "SEARCH_SNIPPET",
                    "snippet:fallback:mode-budget", "NOT_ATTEMPTED", snippet.length());
        }
        return acquisitionService.acquire(text(evidence.getUrl()), text(query), snippet, text(subject));
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
