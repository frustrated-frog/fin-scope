package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchReportQualityValidator {
    private static final Pattern EVIDENCE_REF = Pattern.compile("\\[(E\\d+)]");
    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final String[] GROUNDED_SECTIONS = {"核心结论", "核心证据链", "反方解释与争议"};
    private static final String[] REQUIRED = {"核心结论", "关键认识", "执行摘要", "研究范围与口径",
            "关键事实与数字", "发生了什么", "命题拆解与逐题判断", "核心证据链", "反方解释与争议",
            "机制与情景推演", "最终认识与未知项", "跟踪清单与失效条件", "证据附录"};

    public List<String> validate(String markdown, ResearchThesis thesis, List<ResearchEvidenceDossier> dossier) {
        List<String> issues = new ArrayList<String>();
        if (markdown == null || markdown.length() < 6000) issues.add("REPORT_TOO_SHORT");
        if (markdown != null && markdown.length() > ResearchReportPolicy.MAX_REPORT_CHARACTERS) issues.add("REPORT_TOO_LONG");
        String value = markdown == null ? "" : markdown;
        for (String heading : REQUIRED) if (!value.contains("## " + heading)) issues.add("MISSING_SECTION:" + heading);
        Set<String> allowed = new HashSet<String>();
        for (ResearchEvidenceDossier item : dossier) allowed.add(item.getEvidenceRef());
        Matcher matcher = EVIDENCE_REF.matcher(value);
        while (matcher.find()) if (!allowed.contains(matcher.group(1))) issues.add("INVALID_EVIDENCE_REF:" + matcher.group(1));
        if (thesis != null && thesis.getSubjectName() != null && !value.contains(thesis.getSubjectName())) issues.add("SUBJECT_NOT_GROUNDED");
        if (!dossier.isEmpty()) {
            validateCriticalGrounding(value, allowed, issues);
            validateCitationCoverage(value, dossier, allowed, issues);
            validateCounterEvidence(value, dossier, issues);
            validateSourceDiversity(dossier, issues);
            validateMonitoringConditions(value, issues);
        }
        return new ArrayList<String>(new LinkedHashSet<String>(issues));
    }

    private void validateCriticalGrounding(String markdown, Set<String> allowed, List<String> issues) {
        for (String title : GROUNDED_SECTIONS) {
            if (validRefs(section(markdown, title), allowed).isEmpty()) {
                issues.add("CORE_SECTION_UNGROUNDED:" + title);
            }
        }
    }

    private void validateCitationCoverage(String markdown,
                                            List<ResearchEvidenceDossier> dossier,
                                            Set<String> allowed,
                                            List<String> issues) {
        if (dossier.size() < 3) return;
        String narrative = withoutSection(withoutSection(markdown, "证据附录"), "关键事实与数字");
        int used = validRefs(narrative, allowed).size();
        if (used * 2 < dossier.size()) issues.add("INSUFFICIENT_CITATION_COVERAGE");
    }

    private void validateCounterEvidence(String markdown,
                                         List<ResearchEvidenceDossier> dossier,
                                         List<String> issues) {
        Set<String> counterRefs = new HashSet<String>();
        for (ResearchEvidenceDossier item : dossier) {
            if ("COUNTER".equals(item.getStance())) counterRefs.add(item.getEvidenceRef());
        }
        if (counterRefs.isEmpty()) return;
        Set<String> cited = validRefs(section(markdown, "反方解释与争议"), counterRefs);
        if (cited.isEmpty()) issues.add("COUNTER_EVIDENCE_MISSING");
    }

    private void validateSourceDiversity(List<ResearchEvidenceDossier> dossier, List<String> issues) {
        if (dossier.size() < 3) return;
        Set<String> sources = new HashSet<String>();
        for (ResearchEvidenceDossier item : dossier) {
            String source = text(item.getSourceIdentity(), item.getSourceName());
            if (!source.isEmpty()) sources.add(source);
        }
        if (sources.size() < 2) issues.add("SOURCE_DIVERSITY_INSUFFICIENT");
    }

    private void validateMonitoringConditions(String markdown, List<String> issues) {
        String monitoring = section(markdown, "跟踪清单与失效条件");
        if (!containsAny(monitoring, "失效", "下调", "削弱", "触发", "连续", "低于", "高于", "恶化", "转负")) {
            issues.add("MONITORING_CONDITION_MISSING");
        }
    }

    private Set<String> validRefs(String value, Set<String> allowed) {
        Set<String> refs = new LinkedHashSet<String>();
        Matcher matcher = EVIDENCE_REF.matcher(value == null ? "" : value);
        while (matcher.find()) if (allowed.contains(matcher.group(1))) refs.add(matcher.group(1));
        return refs;
    }

    private String section(String markdown, String title) {
        Matcher matcher = SECTION.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            if (!title.equals(matcher.group(1).trim())) continue;
            int start = matcher.end();
            return matcher.find() ? markdown.substring(start, matcher.start()) : markdown.substring(start);
        }
        return "";
    }

    private String withoutSection(String markdown, String title) {
        String marker = "## " + title;
        int start = markdown.indexOf(marker);
        if (start < 0) return markdown;
        int next = markdown.indexOf("\n## ", start + marker.length());
        return next < 0 ? markdown.substring(0, start) : markdown.substring(0, start) + markdown.substring(next + 1);
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) if (value.contains(keyword)) return true;
        return false;
    }

    private String text(String primary, String fallback) {
        String value = primary == null || primary.trim().isEmpty() ? fallback : primary;
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
