package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FinancialInterpretation {
    private Long id;
    private Long reportId;
    private Long snapshotId;
    private String generationKey;
    private String promptVersion;
    private String modelName;
    private String status;
    private String generationMode;
    private Result result;
    private List<String> validationErrors = new ArrayList<String>();
    private String failureCode;
    private String failureMessage;
    private Long durationMs;
    private boolean snapshotStale;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Data
    public static class Result {
        private String operatingState;
        private String confidence;
        private List<Claim> executiveSummary = new ArrayList<Claim>();
        private List<Claim> periodChanges = new ArrayList<Claim>();
        private List<Claim> crossStatementInsights = new ArrayList<Claim>();
        private List<Dimension> dimensions = new ArrayList<Dimension>();
        private List<Claim> positiveSignals = new ArrayList<Claim>();
        private List<Claim> risks = new ArrayList<Claim>();
        private List<Claim> turningPoints = new ArrayList<Claim>();
        private List<Claim> watchpoints = new ArrayList<Claim>();
        private List<String> limitations = new ArrayList<String>();
        private String disclaimer;

        public static Result fallback(String summary) {
            Result value = new Result();
            value.setOperatingState("INSUFFICIENT_EVIDENCE");
            value.setConfidence("LOW");
            Claim claim = new Claim();
            claim.setClaim(summary);
            claim.setClaimType("FACT");
            value.getExecutiveSummary().add(claim);
            value.setDisclaimer("规则解读仅用于研究，不构成投资建议。");
            return value;
        }
    }

    @Data
    public static class Claim {
        private String claim;
        private String claimType;
        private List<String> refs = new ArrayList<String>();
    }

    @Data
    public static class Dimension {
        private String code;
        private String assessment;
        private String summary;
        private List<String> refs = new ArrayList<String>();
        private List<Claim> details = new ArrayList<Claim>();
    }
}
