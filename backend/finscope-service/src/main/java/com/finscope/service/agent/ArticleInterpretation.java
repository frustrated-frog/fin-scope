package com.finscope.service.agent;

import java.util.ArrayList;
import java.util.List;

public class ArticleInterpretation {
    private String source;
    private String contentType;
    private String topicName;
    private String topicDescription;
    private String oneSentenceSummary;
    private String coreEvent;
    private String importance;
    private List<String> impactTargets = new ArrayList<String>();
    private List<String> keyTerms = new ArrayList<String>();
    private List<String> learningQuestions = new ArrayList<String>();
    private double confidence;
    private String rawJson;

    // 深度解读字段
    private String background;
    private String keyData;
    private String timeline;
    private String relatedParties;
    private String riskFactors;
    private String futureOutlook;
    private String impactOnInvestment;
    private String impactOnStartup;
    private String professionalInsight;
    private String facts;
    private String reasoning;
    private String opinions;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getTopicDescription() {
        return topicDescription;
    }

    public void setTopicDescription(String topicDescription) {
        this.topicDescription = topicDescription;
    }

    public String getOneSentenceSummary() {
        return oneSentenceSummary;
    }

    public void setOneSentenceSummary(String oneSentenceSummary) {
        this.oneSentenceSummary = oneSentenceSummary;
    }

    public String getCoreEvent() {
        return coreEvent;
    }

    public void setCoreEvent(String coreEvent) {
        this.coreEvent = coreEvent;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public List<String> getImpactTargets() {
        return impactTargets;
    }

    public void setImpactTargets(List<String> impactTargets) {
        this.impactTargets = impactTargets;
    }

    public List<String> getKeyTerms() {
        return keyTerms;
    }

    public void setKeyTerms(List<String> keyTerms) {
        this.keyTerms = keyTerms;
    }

    public List<String> getLearningQuestions() {
        return learningQuestions;
    }

    public void setLearningQuestions(List<String> learningQuestions) {
        this.learningQuestions = learningQuestions;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getKeyData() {
        return keyData;
    }

    public void setKeyData(String keyData) {
        this.keyData = keyData;
    }

    public String getTimeline() {
        return timeline;
    }

    public void setTimeline(String timeline) {
        this.timeline = timeline;
    }

    public String getRelatedParties() {
        return relatedParties;
    }

    public void setRelatedParties(String relatedParties) {
        this.relatedParties = relatedParties;
    }

    public String getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(String riskFactors) {
        this.riskFactors = riskFactors;
    }

    public String getFutureOutlook() {
        return futureOutlook;
    }

    public void setFutureOutlook(String futureOutlook) {
        this.futureOutlook = futureOutlook;
    }

    public String getImpactOnInvestment() {
        return impactOnInvestment;
    }

    public void setImpactOnInvestment(String impactOnInvestment) {
        this.impactOnInvestment = impactOnInvestment;
    }

    public String getImpactOnStartup() {
        return impactOnStartup;
    }

    public void setImpactOnStartup(String impactOnStartup) {
        this.impactOnStartup = impactOnStartup;
    }

    public String getProfessionalInsight() {
        return professionalInsight;
    }

    public void setProfessionalInsight(String professionalInsight) {
        this.professionalInsight = professionalInsight;
    }

    public String getFacts() {
        return facts;
    }

    public void setFacts(String facts) {
        this.facts = facts;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getOpinions() {
        return opinions;
    }

    public void setOpinions(String opinions) {
        this.opinions = opinions;
    }
}
