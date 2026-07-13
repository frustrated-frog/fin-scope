package com.finscope.service.research.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvidenceSufficiency {
    private final boolean sufficient;
    private final List<String> warnings;

    private EvidenceSufficiency(boolean sufficient, List<String> warnings) {
        this.sufficient = sufficient;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static EvidenceSufficiency assess(List<ResearchEvidenceCard> evidence) {
        List<String> warnings = new ArrayList<String>();
        Set<String> sources = new HashSet<String>();
        boolean support = false;
        boolean counter = false;
        for (ResearchEvidenceCard card : evidence) {
            sources.add(source(card));
            support |= "SUPPORT".equals(card.getStance());
            counter |= "COUNTER".equals(card.getStance());
        }
        if (evidence.size() < 6) {
            warnings.add("有效证据数量不足 6 条");
        }
        if (sources.size() < 2) {
            warnings.add("独立来源不足 2 个");
        }
        if (!support) {
            warnings.add("缺少明确支持命题的证据");
        }
        if (!counter) {
            warnings.add("缺少反向或风险证据，结论可能存在单边偏差");
        }
        return new EvidenceSufficiency(warnings.isEmpty(), warnings);
    }

    private static String source(ResearchEvidenceCard card) {
        return ResearchSourceIdentity.resolve(card.getArticle());
    }

    public boolean isSufficient() { return sufficient; }
    public List<String> getWarnings() { return warnings; }
}
