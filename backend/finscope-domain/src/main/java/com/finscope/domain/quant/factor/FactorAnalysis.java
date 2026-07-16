package com.finscope.domain.quant.factor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FactorAnalysis {
    /**
     * 因子编码。
     */
    private String factorCode;
    /**
     * 样本数量。
     */
    private int sampleCount;
    /**
     * IC 均值。
     */
    private double icMean;
    /**
     * IC 标准差。
     */
    private double icStd;
    /**
     * IC 信息比率。
     */
    private double icIr;
    /**
     * 正 IC 占比。
     */
    private double positiveIcRatio;
    private double negativeIcRatio;
    private double zeroIcRatio;
    private double icMeanCiLower;
    private double icMeanCiUpper;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 数据集指纹。
     */
    private String datasetFingerprint;
    private String evaluationMode;
    private String researchDirection;
    private double directionAdjustedIcMean;
    private double favorableIcRatio;
    private double directionAdjustedCiLower;
    private double directionAdjustedCiUpper;
    private int totalEligibleDays;
    private int minCrossSectionSize;
    private double coverageRatio;
    private int quantileSampleDays;
    private double quantileSpreadMean;
    private double favorableQuantileSpreadRatio;
    private double quantileMonotonicityMean;
    private double directionAdjustedQuantileSpread;
    private double directionAdjustedMonotonicity;
    private boolean validationEligible;
    private String evaluationPolicyVersion;
    private List<String> blockingReasons = Collections.emptyList();
    private String sampleEvidence;
    private String conclusion;
    private List<String> caveats = Collections.emptyList();

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
    public double getNegativeIcRatio() { return negativeIcRatio; }
    public void setNegativeIcRatio(double negativeIcRatio) { this.negativeIcRatio = negativeIcRatio; }
    public double getZeroIcRatio() { return zeroIcRatio; }
    public void setZeroIcRatio(double zeroIcRatio) { this.zeroIcRatio = zeroIcRatio; }
    public double getIcMeanCiLower() { return icMeanCiLower; }
    public void setIcMeanCiLower(double icMeanCiLower) { this.icMeanCiLower = icMeanCiLower; }
    public double getIcMeanCiUpper() { return icMeanCiUpper; }
    public void setIcMeanCiUpper(double icMeanCiUpper) { this.icMeanCiUpper = icMeanCiUpper; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getDatasetFingerprint() { return datasetFingerprint; }
    public void setDatasetFingerprint(String datasetFingerprint) { this.datasetFingerprint = datasetFingerprint; }
    public String getEvaluationMode() { return evaluationMode; }
    public void setEvaluationMode(String evaluationMode) { this.evaluationMode = evaluationMode; }
    public String getResearchDirection() { return researchDirection; }
    public void setResearchDirection(String researchDirection) { this.researchDirection = researchDirection; }
    public double getDirectionAdjustedIcMean() { return directionAdjustedIcMean; }
    public void setDirectionAdjustedIcMean(double directionAdjustedIcMean) { this.directionAdjustedIcMean = directionAdjustedIcMean; }
    public double getFavorableIcRatio() { return favorableIcRatio; }
    public void setFavorableIcRatio(double favorableIcRatio) { this.favorableIcRatio = favorableIcRatio; }
    public double getDirectionAdjustedCiLower() { return directionAdjustedCiLower; }
    public void setDirectionAdjustedCiLower(double value) { this.directionAdjustedCiLower = value; }
    public double getDirectionAdjustedCiUpper() { return directionAdjustedCiUpper; }
    public void setDirectionAdjustedCiUpper(double value) { this.directionAdjustedCiUpper = value; }
    public int getTotalEligibleDays() { return totalEligibleDays; }
    public void setTotalEligibleDays(int value) { this.totalEligibleDays = value; }
    public int getMinCrossSectionSize() { return minCrossSectionSize; }
    public void setMinCrossSectionSize(int value) { this.minCrossSectionSize = value; }
    public double getCoverageRatio() { return coverageRatio; }
    public void setCoverageRatio(double value) { this.coverageRatio = value; }
    public int getQuantileSampleDays() { return quantileSampleDays; }
    public void setQuantileSampleDays(int value) { quantileSampleDays = value; }
    public double getQuantileSpreadMean() { return quantileSpreadMean; }
    public void setQuantileSpreadMean(double value) { quantileSpreadMean = value; }
    public double getFavorableQuantileSpreadRatio() { return favorableQuantileSpreadRatio; }
    public void setFavorableQuantileSpreadRatio(double value) { favorableQuantileSpreadRatio = value; }
    public double getQuantileMonotonicityMean() { return quantileMonotonicityMean; }
    public void setQuantileMonotonicityMean(double value) { quantileMonotonicityMean = value; }
    public double getDirectionAdjustedQuantileSpread() { return directionAdjustedQuantileSpread; }
    public void setDirectionAdjustedQuantileSpread(double value) { directionAdjustedQuantileSpread = value; }
    public double getDirectionAdjustedMonotonicity() { return directionAdjustedMonotonicity; }
    public void setDirectionAdjustedMonotonicity(double value) { directionAdjustedMonotonicity = value; }
    public boolean isValidationEligible() { return validationEligible; }
    public void setValidationEligible(boolean value) { this.validationEligible = value; }
    public String getEvaluationPolicyVersion() { return evaluationPolicyVersion; }
    public void setEvaluationPolicyVersion(String value) { this.evaluationPolicyVersion = value; }
    public List<String> getBlockingReasons() { return blockingReasons; }
    public void setBlockingReasons(List<String> values) {
        this.blockingReasons = values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
    public String getSampleEvidence() { return sampleEvidence; }
    public void setSampleEvidence(String sampleEvidence) { this.sampleEvidence = sampleEvidence; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public List<String> getCaveats() { return caveats; }
    public void setCaveats(List<String> caveats) {
        this.caveats = caveats == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(caveats));
    }
}
