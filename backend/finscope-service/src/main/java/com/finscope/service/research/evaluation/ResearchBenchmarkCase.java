package com.finscope.service.research.evaluation;

import java.util.ArrayList;
import java.util.List;

public class ResearchBenchmarkCase {
    private String id;
    private String frozenAt;
    private String question;
    private String reportMarkdown;
    private List<String> expectedFacts = new ArrayList<String>();
    private List<ResearchBenchmarkEvidence> evidence = new ArrayList<ResearchBenchmarkEvidence>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFrozenAt() { return frozenAt; }
    public void setFrozenAt(String frozenAt) { this.frozenAt = frozenAt; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReportMarkdown() { return reportMarkdown; }
    public void setReportMarkdown(String reportMarkdown) { this.reportMarkdown = reportMarkdown; }
    public List<String> getExpectedFacts() { return expectedFacts; }
    public void setExpectedFacts(List<String> expectedFacts) { this.expectedFacts = expectedFacts; }
    public List<ResearchBenchmarkEvidence> getEvidence() { return evidence; }
    public void setEvidence(List<ResearchBenchmarkEvidence> evidence) { this.evidence = evidence; }
}
