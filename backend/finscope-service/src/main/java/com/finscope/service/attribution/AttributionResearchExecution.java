package com.finscope.service.attribution;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一次计划执行的逐轨结果，用于让持久化步骤反映真实调用而非展示性状态。 */
public class AttributionResearchExecution {
    private final Map<String, TrackResult> tracks = new LinkedHashMap<String, TrackResult>();

    public TrackResult track(String code) {
        TrackResult result = tracks.get(code);
        if (result == null) {
            result = new TrackResult(code);
            tracks.put(code, result);
        }
        return result;
    }

    public TrackResult get(String code) { return tracks.get(code); }

    public static class TrackResult {
        private final String code;
        private int attempts;
        private int successfulQueries;
        private int evidenceCount;
        private String lastError;
        private boolean budgetStopped;

        TrackResult(String code) { this.code = code; }
        public void attempted() { attempts++; }
        public void succeeded() { successfulQueries++; }
        public void foundEvidence() { evidenceCount++; }
        public String getCode() { return code; }
        public int getAttempts() { return attempts; }
        public int getSuccessfulQueries() { return successfulQueries; }
        public int getEvidenceCount() { return evidenceCount; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        public boolean isBudgetStopped() { return budgetStopped; }
        public void setBudgetStopped(boolean budgetStopped) { this.budgetStopped = budgetStopped; }
        public String status() {
            if (budgetStopped && attempts == 0) return "SKIPPED";
            if (successfulQueries == 0) return attempts == 0 ? "SKIPPED" : "FAILED";
            if (evidenceCount == 0) return "PARTIAL";
            return lastError == null ? "COMPLETED" : "PARTIAL";
        }
    }
}
