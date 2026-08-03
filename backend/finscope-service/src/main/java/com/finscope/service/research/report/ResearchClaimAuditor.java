package com.finscope.service.research.report;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchClaimAuditor {
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z])\\d+(?:\\.\\d+)?%?");
    private static final Pattern PERCENT = Pattern.compile("\\d+(?:\\.\\d+)?%");
    private final ResearchClaimExtractor extractor;

    public ResearchClaimAuditor(ResearchClaimExtractor extractor) {
        this.extractor = extractor;
    }

    public ResearchClaimAudit audit(String markdown, List<ResearchEvidenceDossier> dossier) {
        Map<String, ResearchEvidenceDossier> evidence = new HashMap<String, ResearchEvidenceDossier>();
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) evidence.put(item.getEvidenceRef(), item);
        List<ResearchClaimAuditItem> result = new ArrayList<ResearchClaimAuditItem>();
        for (ResearchClaim claim : extractor.extract(markdown)) result.add(audit(claim, evidence));
        return new ResearchClaimAudit(result);
    }

    private ResearchClaimAuditItem audit(ResearchClaim claim, Map<String, ResearchEvidenceDossier> evidence) {
        if (claim.getEvidenceRefs().isEmpty()) {
            return item(claim, "UNSUPPORTED", "事实数字或日期没有绑定证据引用");
        }
        List<String> excerpts = new ArrayList<String>();
        for (String ref : claim.getEvidenceRefs()) {
            ResearchEvidenceDossier bound = evidence.get(ref);
            if (bound == null) return item(claim, "UNSUPPORTED", "引用编号不在证据白名单：" + ref);
            excerpts.add(text(bound.getFactExcerpt()));
        }
        if (hasNumericConflict(claim, excerpts)) {
            return item(claim, "CONFLICT", "绑定证据对同一百分比事实给出冲突数值");
        }
        String combined = String.join(" ", excerpts);
        if (hasCompletionConflict(claim.getText(), combined)) {
            return item(claim, "CONFLICT", "主张声称事项已经完成，但绑定证据仍显示尚未完成");
        }
        for (String number : claim.getNumbers()) {
            if (!containsNumber(combined, number)) {
                return item(claim, "UNSUPPORTED", "引用材料不包含主张中的数字或日期：" + number);
            }
        }
        double overlap = overlap(claim.getText(), combined);
        if (contradictory(claim.getText(), combined) && overlap >= 0.08D) {
            return item(claim, "PARTIAL", "引用支持部分事实，但包含相反限定条件");
        }
        if (overlap >= 0.20D || (!claim.getNumbers().isEmpty() && overlap >= 0.08D)) {
            return item(claim, "SUPPORTED", "引用材料覆盖主张的关键事实");
        }
        if (overlap >= 0.08D) return item(claim, "PARTIAL", "引用只覆盖主张的一部分");
        return item(claim, "UNSUPPORTED", "主张与引用材料缺少足够语义重合");
    }

    private boolean hasNumericConflict(ResearchClaim claim, List<String> excerpts) {
        Set<String> claimedPercent = matches(PERCENT, claim.getText());
        if (claimedPercent.isEmpty() || excerpts.size() < 2) return false;
        Set<String> evidencePercent = new HashSet<String>();
        for (String excerpt : excerpts) evidencePercent.addAll(matches(PERCENT, excerpt));
        return evidencePercent.size() > 1 && evidencePercent.containsAll(claimedPercent);
    }

    private boolean hasCompletionConflict(String claim, String evidence) {
        String claimed = text(claim);
        String bound = text(evidence);
        boolean claimsCompletion = containsAny(claimed, "获批", "获得批准", "已经批准", "已批准",
                "已经完成", "已完成", "approved", "completed")
                || (containsAny(claimed, "已经", "已获得", "已取得") && claimed.contains("批准"));
        boolean evidencePending = containsAny(bound, "尚未", "未获批", "未批准", "未完成", "仍在审批",
                "结果尚未", "pending", "not approved", "not completed");
        return claimsCompletion && evidencePending;
    }

    private boolean containsNumber(String evidence, String number) {
        for (String candidate : matches(NUMBER, evidence)) if (candidate.equals(number)) return true;
        return false;
    }

    private Set<String> matches(Pattern pattern, String value) {
        Set<String> result = new HashSet<String>();
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private double overlap(String claim, String evidence) {
        Set<String> claimTerms = terms(claim);
        Set<String> evidenceTerms = terms(evidence);
        if (claimTerms.isEmpty()) return 0D;
        int matched = 0;
        for (String term : claimTerms) if (evidenceTerms.contains(term)) matched++;
        return matched / (double) claimTerms.size();
    }

    private Set<String> terms(String value) {
        Set<String> result = new HashSet<String>();
        String normalized = text(value).replaceAll("[^\\p{L}\\p{N}%]+", " ");
        for (String token : normalized.split("\\s+")) {
            if (token.length() < 2) continue;
            if (token.matches(".*[\\u4e00-\\u9fff].*")) {
                for (int index = 0; index + 2 <= token.length(); index++) {
                    result.add(token.substring(index, index + 2));
                }
            } else result.add(token);
        }
        return result;
    }

    private boolean contradictory(String claim, String evidence) {
        String c = text(claim);
        String e = text(evidence);
        return (containsAny(c, "全面恢复", "已经恢复", "确定增长", "必然")
                && containsAny(e, "仍低于", "尚未", "不确定", "风险", "下降", "放缓"));
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private ResearchClaimAuditItem item(ResearchClaim claim, String status, String reason) {
        return new ResearchClaimAuditItem(claim, status, reason);
    }

    private String text(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
