package com.finscope.domain.intake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CandidateReview {
    private String chineseTitle;
    private String decisionSummary;
    private List<String> keyFacts = Collections.emptyList();
    private String whyItMatters;
    private String noveltyJudgment;
    private List<String> riskFlags = Collections.emptyList();
    private int score;
    private String recommendation;
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
