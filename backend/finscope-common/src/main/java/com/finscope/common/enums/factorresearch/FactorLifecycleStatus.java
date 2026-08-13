package com.finscope.common.enums.factorresearch;

/**
 * A1 因子从候选到生产资格及退出的完整生命周期。
 */
public enum FactorLifecycleStatus {
    CANDIDATE,
    DEFINITION_REVIEWED,
    IMPLEMENTED,
    CALCULATION_VERIFIED,
    EXPLORATORY,
    VALIDATED,
    PRODUCTION_ELIGIBLE,
    INVALIDATED,
    RETIRED
}
