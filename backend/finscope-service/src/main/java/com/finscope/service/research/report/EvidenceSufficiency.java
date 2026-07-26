package com.finscope.service.research.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvidenceSufficiency {
    private final boolean sufficient;
    private final int evidenceCount;
    private final int sourceCount;
    private final int supportCount;
    private final int counterCount;
    private final List<String> warnings;

    private EvidenceSufficiency(boolean sufficient,
                                int evidenceCount,
                                int sourceCount,
                                int supportCount,
                                int counterCount,
                                List<String> warnings) {
        this.sufficient = sufficient;
        this.evidenceCount = evidenceCount;
        this.sourceCount = sourceCount;
        this.supportCount = supportCount;
        this.counterCount = counterCount;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public static EvidenceSufficiency assess(List<ResearchEvidenceCard> evidence) {
        Set<String> sources = new HashSet<String>();
        int supportCount = 0;
        int counterCount = 0;
        List<ResearchEvidenceCard> safeEvidence = evidence == null
                ? Collections.<ResearchEvidenceCard>emptyList() : evidence;
        for (ResearchEvidenceCard card : safeEvidence) {
            sources.add(source(card));
            if ("SUPPORT".equals(card.getStance())) {
                supportCount++;
            }
            if ("COUNTER".equals(card.getStance())) {
                counterCount++;
            }
        }
        return fromCounts(safeEvidence.size(), sources.size(), supportCount, counterCount);
    }

    public static EvidenceSufficiency fromCounts(int evidenceCount,
                                                 int sourceCount,
                                                 int supportCount,
                                                 int counterCount) {
        if (evidenceCount < 0 || sourceCount < 0 || supportCount < 0 || counterCount < 0
                || supportCount + counterCount > evidenceCount) {
            throw new IllegalArgumentException("证据充分性计数不合法");
        }
        List<String> warnings = new ArrayList<String>();
        if (evidenceCount < 6) {
            warnings.add("有效证据数量不足 6 条");
        }
        if (sourceCount < 2) {
            warnings.add("独立来源不足 2 个");
        }
        if (supportCount == 0) {
            warnings.add("缺少明确支持命题的证据");
        }
        if (counterCount == 0) {
            warnings.add("缺少反向或风险证据，结论可能存在单边偏差");
        }
        return new EvidenceSufficiency(warnings.isEmpty(), evidenceCount, sourceCount,
                supportCount, counterCount, warnings);
    }

    private static String source(ResearchEvidenceCard card) {
        return ResearchSourceIdentity.resolve(card.getArticle());
    }

    public boolean isSufficient() { return sufficient; }
    public int getEvidenceCount() { return evidenceCount; }
    public int getSourceCount() { return sourceCount; }
    public int getSupportCount() { return supportCount; }
    public int getCounterCount() { return counterCount; }
    public List<String> getWarnings() { return warnings; }
}
