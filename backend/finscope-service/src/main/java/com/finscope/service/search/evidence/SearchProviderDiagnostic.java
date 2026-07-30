package com.finscope.service.search.evidence;

public class SearchProviderDiagnostic {
    private final String providerCode;
    private final long latencyMs;
    private final int rawHitCount;
    private final boolean failed;
    private final String errorType;

    public SearchProviderDiagnostic(String providerCode, long latencyMs, int rawHitCount,
                                    boolean failed, String errorType) {
        this.providerCode = providerCode;
        this.latencyMs = latencyMs;
        this.rawHitCount = rawHitCount;
        this.failed = failed;
        this.errorType = errorType;
    }

    public String getProviderCode() { return providerCode; }
    public long getLatencyMs() { return latencyMs; }
    public int getRawHitCount() { return rawHitCount; }
    public boolean isFailed() { return failed; }
    public String getErrorType() { return errorType; }
}
