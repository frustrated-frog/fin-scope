package com.finscope.domain.quant.discovery;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StockDiscoveryEvaluationRequest {
    private String asOfDate;
    private int pendingCount;
    private List<OutcomeObservation> observations = new ArrayList<>();
    private List<ModelObservation> modelObservations = new ArrayList<>();

    @Data
    public static class OutcomeObservation {
        private Long runId;
        private String instrumentCode;
        private String asOfDate;
        private int horizonDays;
        private boolean admitted;
        private Integer finalRank;
        private Double calibratedProbability;
        private double actualNetReturn;
        private String actualDirection;
        private List<String> sectorNames = new ArrayList<>();
    }

    @Data
    public static class ModelObservation {
        private Long runId;
        private String instrumentCode;
        private String asOfDate;
        private int horizonDays;
        private String modelCode;
        private String modelName;
        private String role;
        private double calibratedProbability;
        private String shadowDecision;
        private String qualificationStatus;
        private String actualDirection;
    }
}
