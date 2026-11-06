package com.finscope.common.enums.watchlistresearch;

public enum ResearchScanStatus {
    RUNNING,
    READY,
    PARTIAL,
    FAILED;

    public boolean isCompleted() {
        return this == READY || this == PARTIAL;
    }
}
