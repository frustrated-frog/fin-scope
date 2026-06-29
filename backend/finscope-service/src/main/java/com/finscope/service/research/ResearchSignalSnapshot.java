package com.finscope.service.research;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ResearchSignalSnapshot {
    private final String anchorText;
    private final String primaryClaim;
    private final String searchableText;
    private final boolean officialSignal;
    private final boolean dataSignal;
    private final boolean timelineSignal;
    private final boolean policySignal;
    private final boolean inflationSignal;
    private final boolean goldSignal;
    private final boolean ipoSignal;
    private final boolean earningsSignal;
    private final boolean aiFundingSignal;
    private final boolean aiProductSignal;
    private final boolean aiEcosystemSignal;
    private final int evidenceStrength;
    private final int importanceScore;

    private ResearchSignalSnapshot(String anchorText,
                                   String primaryClaim,
                                   String searchableText,
                                   boolean officialSignal,
                                   boolean dataSignal,
                                   boolean timelineSignal,
                                   boolean policySignal,
                                   boolean inflationSignal,
                                   boolean goldSignal,
                                   boolean ipoSignal,
                                   boolean earningsSignal,
                                   boolean aiFundingSignal,
                                   boolean aiProductSignal,
                                   boolean aiEcosystemSignal,
                                   int evidenceStrength,
                                   int importanceScore) {
        this.anchorText = anchorText;
        this.primaryClaim = primaryClaim;
        this.searchableText = searchableText;
        this.officialSignal = officialSignal;
        this.dataSignal = dataSignal;
        this.timelineSignal = timelineSignal;
        this.policySignal = policySignal;
        this.inflationSignal = inflationSignal;
        this.goldSignal = goldSignal;
        this.ipoSignal = ipoSignal;
        this.earningsSignal = earningsSignal;
        this.aiFundingSignal = aiFundingSignal;
        this.aiProductSignal = aiProductSignal;
        this.aiEcosystemSignal = aiEcosystemSignal;
        this.evidenceStrength = evidenceStrength;
        this.importanceScore = importanceScore;
    }

    static ResearchSignalSnapshot from(EventCluster event, Article article, List<EvidenceItem> evidenceItems) {
        List<String> evidenceClaims = new ArrayList<String>();
        boolean officialSignal = false;
        boolean dataSignal = false;
        boolean timelineSignal = false;
        for (EvidenceItem item : evidenceItems) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.isBlank(item.getClaim())) {
                evidenceClaims.add(item.getClaim());
            }
            if (ResearchEnums.SOURCE_TIER_OFFICIAL.equals(item.getSourceTier())
                    || ResearchEnums.SOURCE_TIER_REGULATOR.equals(item.getSourceTier())
                    || ResearchEnums.SOURCE_TIER_COMPANY.equals(item.getSourceTier())) {
                officialSignal = true;
            }
            if (ResearchEnums.EVIDENCE_DATA.equals(item.getEvidenceType())) {
                dataSignal = true;
            }
            if (ResearchEnums.EVIDENCE_TIMELINE.equals(item.getEvidenceType())) {
                timelineSignal = true;
            }
        }
        String searchableText = joinText(
                event == null ? null : event.getCanonicalTitle(),
                event == null ? null : event.getSummary(),
                String.join(" ", evidenceClaims),
                article == null ? null : article.getTitle(),
                article == null ? null : article.getSummary(),
                article == null ? null : article.getBody());
        int importanceScore = event != null && event.getImportanceScore() != null ? event.getImportanceScore() : 60;
        boolean policySignal = containsText(searchableText,
                "mlf", "lpr", "rrr", "降准", "逆回购", "中期借贷便利", "货币政策", "流动性", "政策利率", "公开市场操作");
        boolean inflationSignal = containsText(searchableText,
                "cpi", "pce", "通胀", "核心通胀", "物价", "inflation");
        boolean goldSignal = containsText(searchableText,
                "黄金", "gold", "贵金属", "etf", "实际利率", "资金流入", "资金流出");
        boolean ipoSignal = containsText(searchableText,
                "ipo", "招股", "上市", "聆讯", "sec", "hkex");
        boolean earningsSignal = containsText(searchableText,
                "财报", "营收", "利润", "毛利率", "指引", "guidance", "earnings");
        boolean aiFundingSignal = containsText(searchableText,
                "融资", "funding", "融资轮", "估值", "募资");
        boolean aiProductSignal = containsText(searchableText,
                "发布", "上线", "模型", "agent", "api", "开源", "release");
        boolean aiEcosystemSignal = containsText(searchableText,
                "github", "开发者", "生态", "工作流", "插件", "tooling");
        int evidenceStrength = clamp(45
                + Math.min(20, evidenceItems.size() * 8)
                + (officialSignal ? 15 : 0)
                + (dataSignal ? 8 : 0)
                + (timelineSignal ? 5 : 0)
                + Math.min(18, importanceScore / 5));
        return new ResearchSignalSnapshot(
                anchorText(event, article),
                StringUtils.firstNonBlank(String.join(" ", evidenceClaims),
                        event == null ? null : event.getSummary(),
                        article == null ? null : article.getSummary(),
                        article == null ? null : article.getTitle(),
                        "这个事件"),
                searchableText,
                officialSignal,
                dataSignal,
                timelineSignal,
                policySignal,
                inflationSignal,
                goldSignal,
                ipoSignal,
                earningsSignal,
                aiFundingSignal,
                aiProductSignal,
                aiEcosystemSignal,
                evidenceStrength,
                importanceScore);
    }

    String anchorText() {
        return anchorText;
    }

    String primaryClaim() {
        return primaryClaim;
    }

    boolean officialSignal() {
        return officialSignal;
    }

    boolean dataSignal() {
        return dataSignal;
    }

    boolean timelineSignal() {
        return timelineSignal;
    }

    boolean policySignal() {
        return policySignal;
    }

    boolean inflationSignal() {
        return inflationSignal;
    }

    boolean goldSignal() {
        return goldSignal;
    }

    boolean ipoSignal() {
        return ipoSignal;
    }

    boolean earningsSignal() {
        return earningsSignal;
    }

    boolean aiFundingSignal() {
        return aiFundingSignal;
    }

    boolean aiProductSignal() {
        return aiProductSignal;
    }

    boolean aiEcosystemSignal() {
        return aiEcosystemSignal;
    }

    int evidenceStrength() {
        return evidenceStrength;
    }

    int importanceScore() {
        return importanceScore;
    }

    boolean containsAny(String... keywords) {
        return containsText(searchableText, keywords);
    }

    private static String anchorText(EventCluster event, Article article) {
        String title = StringUtils.firstNonBlank(
                event == null ? null : event.getCanonicalTitle(),
                article == null ? null : article.getTitle(),
                "这个事件");
        List<String> parts = new ArrayList<String>();
        for (String item : title.split("[，,：:、\\s]+")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
            if (parts.size() >= 2) {
                break;
            }
        }
        return parts.isEmpty() ? title : String.join("", parts);
    }

    private static String joinText(String... parts) {
        List<String> values = new ArrayList<String>();
        for (String part : parts) {
            if (!StringUtils.isBlank(part)) {
                values.add(part.trim());
            }
        }
        return String.join(" ", values).toLowerCase(Locale.ROOT);
    }

    private static boolean containsText(String text, String... keywords) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
