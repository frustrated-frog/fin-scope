package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalBusinessAspect;
import com.finscope.common.enums.fundamentals.FundamentalFindingSupport;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.learningcard.StockLearningCardSection;
import com.finscope.domain.learningcard.StockLearningCardEvidence;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 投影已有公开研究，证据编号仅在所属维度内解析，不复制模型或检索流程。 */
public class FundamentalBusinessProjector {
    private static final Set<String> SUPPORT = Set.of("SUPPORTED", "PARTIALLY_SUPPORTED", "CONTRADICTED");

    public List<FundamentalBusinessFinding> project(StockLearningCardRun run) {
        var results = new ArrayList<FundamentalBusinessFinding>();
        if (run == null || "RUNNING".equals(run.getStatus())) {
            return results;
        }
        for (FundamentalBusinessAspect aspect : FundamentalBusinessAspect.values()) {
            var claim = run.getClaims().stream().filter(value -> "READY".equals(value.getStatus())
                    && aspect.getDimension().equals(value.getDimensionCode())).findFirst().orElse(null);
            if (claim == null) {
                continue;
            }
            var section = claim.getSections().stream().filter(value -> aspect.getSectionKey().equals(value.getKey())
                    && value.getVerificationStatus() != null && SUPPORT.contains(value.getVerificationStatus())
                    && value.getContent() != null && !value.getContent().isBlank())
                    .findFirst().orElse(null);
            if (section == null) {
                continue;
            }
            var sources = sources(run, aspect, section);
            if (sources.isEmpty()) {
                continue;
            }
            var finding = new FundamentalBusinessFinding();
            finding.setAspect(aspect);
            finding.setLabel(aspect.getLabel());
            finding.setNoteField(aspect.getNoteField());
            finding.setContent(section.getContent());
            finding.setSupport(FundamentalFindingSupport.valueOf(section.getVerificationStatus()));
            finding.setRunId(run.getId());
            finding.setGeneratedAt(run.getCompletedAt());
            finding.setSources(sources);
            results.add(finding);
        }
        return results;
    }

    private List<FundamentalBusinessSource> sources(StockLearningCardRun run, FundamentalBusinessAspect aspect, StockLearningCardSection section) {
        var sources = new ArrayList<FundamentalBusinessSource>();
        for (String ref : section.getEvidenceRefs().stream().distinct().toList()) {
            if (ref == null) {
                return List.of();
            }
            var evidence = run.getEvidence().stream().filter(value -> aspect.getDimension().equals(value.getDimensionCode())
                    && ref.equals(value.getEvidenceCode()) && usable(value)).findFirst().orElse(null);
            if (evidence == null) {
                return List.of();
            }
            var source = new FundamentalBusinessSource();
            source.setEvidenceRef(ref);
            source.setTitle(evidence.getTitle());
            source.setUrl(evidence.getUrl());
            source.setPublishedAt(evidence.getPublishedAt());
            source.setContentOrigin(evidence.getContentOrigin());
            sources.add(source);
        }
        return sources;
    }

    private boolean usable(StockLearningCardEvidence evidence) {
        if (evidence.content() == null || evidence.content().isBlank() || evidence.getUrl() == null) {
            return false;
        }
        try {
            URI url = URI.create(evidence.getUrl());
            return url.getScheme() != null && Set.of("http", "https").contains(url.getScheme())
                    && url.getHost() != null && url.getUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
