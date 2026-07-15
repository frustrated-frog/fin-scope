package com.finscope.service.factorresearch;

/** Versioned, deterministic admission policy for cross-sectional factor evidence. */
public final class FactorValidationPolicy {
    public static final String VERSION = "cross-sectional-evidence-v2";
    public static final int MIN_CROSS_SECTION_SIZE = 10;
    public static final int MIN_VALID_IC_DAYS = 60;
    public static final double MIN_DIRECTION_ADJUSTED_IC = 0.02d;
    public static final double MIN_FAVORABLE_RATIO = 0.55d;
    public static final double MIN_COVERAGE_RATIO = 0.80d;

    private FactorValidationPolicy() { }
}
