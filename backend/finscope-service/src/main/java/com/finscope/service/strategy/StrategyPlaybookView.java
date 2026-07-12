package com.finscope.service.strategy;

public class StrategyPlaybookView {
    private final String code;
    private final String title;
    private final String scope;
    private final String summary;
    private final String cadence;
    private final String riskBoundary;
    private final String status;
    private final String note;
    private final long revision;

    public StrategyPlaybookView(String code, String title, String scope, String summary,
                                String cadence, String riskBoundary, String status,
                                String note, long revision) {
        this.code = code;
        this.title = title;
        this.scope = scope;
        this.summary = summary;
        this.cadence = cadence;
        this.riskBoundary = riskBoundary;
        this.status = status;
        this.note = note;
        this.revision = revision;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getScope() { return scope; }
    public String getSummary() { return summary; }
    public String getCadence() { return cadence; }
    public String getRiskBoundary() { return riskBoundary; }
    public String getStatus() { return status; }
    public String getNote() { return note; }
    public long getRevision() { return revision; }
}
