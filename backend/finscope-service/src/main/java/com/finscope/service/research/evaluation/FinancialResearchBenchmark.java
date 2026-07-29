package com.finscope.service.research.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.service.research.report.ResearchClaim;
import com.finscope.service.research.report.ResearchClaimAudit;
import com.finscope.service.research.report.ResearchClaimAuditItem;
import com.finscope.service.research.report.ResearchClaimAuditor;
import com.finscope.service.research.report.ResearchEvidenceDossier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FinancialResearchBenchmark {
    public static final String VERSION = "financial-grounding-v1";
    private final ObjectMapper objectMapper;
    private final ResearchClaimAuditor auditor;

    public FinancialResearchBenchmark(ObjectMapper objectMapper, ResearchClaimAuditor auditor) {
        this.objectMapper = objectMapper;
        this.auditor = auditor;
    }

    public ResearchBenchmarkRun run(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Benchmark 输入不能为空");
        try {
            List<ResearchBenchmarkCase> cases = objectMapper.readValue(input,
                    new TypeReference<List<ResearchBenchmarkCase>>() { });
            List<ResearchBenchmarkCaseResult> result = new ArrayList<ResearchBenchmarkCaseResult>();
            for (ResearchBenchmarkCase item : cases) result.add(evaluate(item));
            return new ResearchBenchmarkRun(VERSION, result);
        } catch (Exception error) {
            throw new IllegalStateException("金融研究 Benchmark 读取失败", error);
        }
    }

    private ResearchBenchmarkCaseResult evaluate(ResearchBenchmarkCase item) {
        List<ResearchEvidenceDossier> dossier = dossier(item.getEvidence());
        ResearchClaimAudit audit = auditor.audit(item.getReportMarkdown(), dossier);
        int cited = 0;
        for (ResearchClaimAuditItem auditItem : audit.getItems()) {
            ResearchClaim claim = auditItem.getClaim();
            if (!claim.getEvidenceRefs().isEmpty()) cited++;
        }
        ResearchGroundingMetrics metrics = new ResearchGroundingMetrics(
                audit.getClaimCount(), cited, audit.getSupportedCount(),
                ratio(cited, audit.getClaimCount()), ratio(audit.getSupportedCount(), audit.getClaimCount()),
                keyFactCoverage(item), primaryRatio(item.getEvidence()), counterCoverage(item),
                accessibility(item.getEvidence()), freshness(item));
        return new ResearchBenchmarkCaseResult(item.getId(), item.getQuestion(), metrics);
    }

    private List<ResearchEvidenceDossier> dossier(List<ResearchBenchmarkEvidence> evidence) {
        List<ResearchEvidenceDossier> result = new ArrayList<ResearchEvidenceDossier>();
        if (evidence == null) return result;
        for (ResearchBenchmarkEvidence item : evidence) {
            result.add(new ResearchEvidenceDossier(item.getRef(), null, null, item.getSourceIdentity(),
                    item.getSourceName(), item.getSourceTier(), item.getTitle(), parseDateTime(item.getPublishedAt()),
                    item.getUrl(), item.getExcerpt(), item.getStance(), 100));
        }
        return result;
    }

    private double keyFactCoverage(ResearchBenchmarkCase item) {
        List<String> facts = item.getExpectedFacts();
        if (facts == null || facts.isEmpty()) return 1D;
        String report = normalize(item.getReportMarkdown());
        int covered = 0;
        for (String fact : facts) if (report.contains(normalize(fact))) covered++;
        return ratio(covered, facts.size());
    }

    private double primaryRatio(List<ResearchBenchmarkEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return 0D;
        int primary = 0;
        for (ResearchBenchmarkEvidence item : evidence) {
            String tier = text(item.getSourceTier()).toUpperCase(Locale.ROOT);
            if ("T1".equals(tier) || "PRIMARY".equals(tier)) primary++;
        }
        return ratio(primary, evidence.size());
    }

    private double counterCoverage(ResearchBenchmarkCase item) {
        Set<String> counter = new HashSet<String>();
        if (item.getEvidence() != null) for (ResearchBenchmarkEvidence evidence : item.getEvidence()) {
            if ("COUNTER".equalsIgnoreCase(evidence.getStance())) counter.add(evidence.getRef());
        }
        if (counter.isEmpty()) return 1D;
        String report = text(item.getReportMarkdown());
        int cited = 0;
        for (String ref : counter) if (report.contains("[" + ref + "]")) cited++;
        return ratio(cited, counter.size());
    }

    private double accessibility(List<ResearchBenchmarkEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return 0D;
        int accessible = 0;
        for (ResearchBenchmarkEvidence item : evidence) {
            try {
                URI uri = URI.create(item.getUrl());
                if (uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) accessible++;
            } catch (Exception ignored) {
                // Invalid frozen citation.
            }
        }
        return ratio(accessible, evidence.size());
    }

    private double freshness(ResearchBenchmarkCase item) {
        if (item.getEvidence() == null || item.getEvidence().isEmpty()) return 0D;
        LocalDate frozenAt = LocalDate.parse(item.getFrozenAt());
        int fresh = 0;
        for (ResearchBenchmarkEvidence evidence : item.getEvidence()) {
            LocalDateTime published = parseDateTime(evidence.getPublishedAt());
            if (published != null && !published.toLocalDate().isAfter(frozenAt)
                    && !published.toLocalDate().isBefore(frozenAt.minusDays(365))) fresh++;
        }
        return ratio(fresh, item.getEvidence().size());
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length()))).atStartOfDay();
        }
    }

    private double ratio(int numerator, int denominator) {
        if (denominator <= 0) return 0D;
        return Math.round((numerator / (double) denominator) * 10_000D) / 10_000D;
    }

    private String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}%]+", "");
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
