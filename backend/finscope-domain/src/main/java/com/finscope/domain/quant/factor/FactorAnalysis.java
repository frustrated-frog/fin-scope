package com.finscope.domain.quant.factor;

public class FactorAnalysis {
    private String factorCode;
    private int sampleCount;
    private double icMean;
    private double icStd;
    private double icIr;
    private double positiveIcRatio;
    private Long datasetId;
    private String datasetFingerprint;

    public String getFactorCode() { return factorCode; }
    public void setFactorCode(String factorCode) { this.factorCode = factorCode; }
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    public double getIcMean() { return icMean; }
    public void setIcMean(double icMean) { this.icMean = icMean; }
    public double getIcStd() { return icStd; }
    public void setIcStd(double icStd) { this.icStd = icStd; }
    public double getIcIr() { return icIr; }
    public void setIcIr(double icIr) { this.icIr = icIr; }
    public double getPositiveIcRatio() { return positiveIcRatio; }
    public void setPositiveIcRatio(double positiveIcRatio) { this.positiveIcRatio = positiveIcRatio; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getDatasetFingerprint() { return datasetFingerprint; }
    public void setDatasetFingerprint(String datasetFingerprint) { this.datasetFingerprint = datasetFingerprint; }
}
