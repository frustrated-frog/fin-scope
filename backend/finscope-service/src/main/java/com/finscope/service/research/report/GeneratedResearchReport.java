package com.finscope.service.research.report;

public class GeneratedResearchReport {
    private final String title;
    private final String conclusion;
    private final String conclusionDirection;
    private final String confidence;
    private final String executiveSummary;
    private final String markdown;
    private final String generationMode;

    GeneratedResearchReport(String title, String conclusion, String conclusionDirection, String confidence,
                            String executiveSummary, String markdown, String generationMode) {
        this.title = title;
        this.conclusion = conclusion;
        this.conclusionDirection = conclusionDirection;
        this.confidence = confidence;
        this.executiveSummary = executiveSummary;
        this.markdown = markdown;
        this.generationMode = generationMode;
    }

    public String getTitle() { return title; }
    public String getConclusion() { return conclusion; }
    public String getConclusionDirection() { return conclusionDirection; }
    public String getConfidence() { return confidence; }
    public String getExecutiveSummary() { return executiveSummary; }
    public String getMarkdown() { return markdown; }
    public String getGenerationMode() { return generationMode; }
}
