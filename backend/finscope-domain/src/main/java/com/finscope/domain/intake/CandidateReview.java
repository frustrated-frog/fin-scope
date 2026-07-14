package com.finscope.domain.intake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CandidateReview {
    /**
     * 中文标题。
     */
    private String chineseTitle;
    /**
     * 审核决策摘要。
     */
    private String decisionSummary;
    /**
     * 关键事实列表。
     */
    private List<String> keyFacts = Collections.emptyList();
    /**
     * 重要性说明。
     */
    private String whyItMatters;
    /**
     * 新意判断。
     */
    private String noveltyJudgment;
    /**
     * 风险提示列表。
     */
    private List<String> riskFlags = Collections.emptyList();
    /**
     * 评分。
     */
    private int score;
    /**
     * 推荐结论。
     */
    private String recommendation;
    /**
     * 原因说明。
     */
    private String reason;

    public String getChineseTitle() {
        return chineseTitle;
    }

    public void setChineseTitle(String chineseTitle) {
        this.chineseTitle = chineseTitle;
    }

    public String getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(String decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(keyFacts));
    }

    public String getWhyItMatters() {
        return whyItMatters;
    }

    public void setWhyItMatters(String whyItMatters) {
        this.whyItMatters = whyItMatters;
    }

    public String getNoveltyJudgment() {
        return noveltyJudgment;
    }

    public void setNoveltyJudgment(String noveltyJudgment) {
        this.noveltyJudgment = noveltyJudgment;
    }

    public List<String> getRiskFlags() {
        return riskFlags;
    }

    public void setRiskFlags(List<String> riskFlags) {
        this.riskFlags = riskFlags == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(riskFlags));
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
