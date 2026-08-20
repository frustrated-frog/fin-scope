package com.finscope.domain.investmentobservation;

public class InvestmentObservationScoreDimension {
    private String code;
    private String label;
    private int score;
    private int maxScore;
    private String explanation;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getMaxScore() { return maxScore; }
    public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
