package com.finscope.service.research.evaluation;

public final class ResearchGroundingMetrics {
    private final int claimCount;
    private final int citedClaimCount;
    private final int supportedClaimCount;
    private final double citationCoverageRate;
    private final double claimSupportRate;
    private final double keyFactCoverageRate;
    private final double primarySourceRatio;
    private final double counterEvidenceCoverage;
    private final double citationAccessibilityRate;
    private final double freshnessRate;

    public ResearchGroundingMetrics(int claimCount, int citedClaimCount, int supportedClaimCount,
                                    double citationCoverageRate, double claimSupportRate,
                                    double keyFactCoverageRate, double primarySourceRatio,
                                    double counterEvidenceCoverage, double citationAccessibilityRate,
                                    double freshnessRate) {
        this.claimCount = claimCount;
        this.citedClaimCount = citedClaimCount;
        this.supportedClaimCount = supportedClaimCount;
        this.citationCoverageRate = bounded(citationCoverageRate);
        this.claimSupportRate = bounded(claimSupportRate);
        this.keyFactCoverageRate = bounded(keyFactCoverageRate);
        this.primarySourceRatio = bounded(primarySourceRatio);
        this.counterEvidenceCoverage = bounded(counterEvidenceCoverage);
        this.citationAccessibilityRate = bounded(citationAccessibilityRate);
        this.freshnessRate = bounded(freshnessRate);
    }

    public int getClaimCount() { return claimCount; }
    public int getCitedClaimCount() { return citedClaimCount; }
    public int getSupportedClaimCount() { return supportedClaimCount; }
    public double getCitationCoverageRate() { return citationCoverageRate; }
    public double getClaimSupportRate() { return claimSupportRate; }
    public double getKeyFactCoverageRate() { return keyFactCoverageRate; }
    public double getPrimarySourceRatio() { return primarySourceRatio; }
    public double getCounterEvidenceCoverage() { return counterEvidenceCoverage; }
    public double getCitationAccessibilityRate() { return citationAccessibilityRate; }
    public double getFreshnessRate() { return freshnessRate; }

    private double bounded(double value) { return Math.max(0D, Math.min(1D, value)); }
}
