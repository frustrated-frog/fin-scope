package com.finscope.domain.strategy;

public class StrategyPlaybookRule {
    private Long id;
    private Long playbookId;
    private String sectionCode;
    private String sectionTitle;
    private String ruleType;
    private String ruleText;
    private String testability;
    private Integer sourcePage;
    private String parameterJson;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlaybookId() { return playbookId; }
    public void setPlaybookId(Long playbookId) { this.playbookId = playbookId; }
    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getRuleText() { return ruleText; }
    public void setRuleText(String ruleText) { this.ruleText = ruleText; }
    public String getTestability() { return testability; }
    public void setTestability(String testability) { this.testability = testability; }
    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }
    public String getParameterJson() { return parameterJson; }
    public void setParameterJson(String parameterJson) { this.parameterJson = parameterJson; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
