package com.finscope.domain.quant.catalog;

public class QuantStrategyCatalogEntry {
    private String externalKey;
    private String title;
    private Double reportedSharpe;
    private Double reportedVolatility;
    private String rebalanceCadence;
    private String implementationUrl;
    private String paperUrl;

    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Double getReportedSharpe() { return reportedSharpe; }
    public void setReportedSharpe(Double reportedSharpe) { this.reportedSharpe = reportedSharpe; }
    public Double getReportedVolatility() { return reportedVolatility; }
    public void setReportedVolatility(Double reportedVolatility) { this.reportedVolatility = reportedVolatility; }
    public String getRebalanceCadence() { return rebalanceCadence; }
    public void setRebalanceCadence(String rebalanceCadence) { this.rebalanceCadence = rebalanceCadence; }
    public String getImplementationUrl() { return implementationUrl; }
    public void setImplementationUrl(String implementationUrl) { this.implementationUrl = implementationUrl; }
    public String getPaperUrl() { return paperUrl; }
    public void setPaperUrl(String paperUrl) { this.paperUrl = paperUrl; }
}
