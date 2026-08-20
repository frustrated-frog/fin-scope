package com.finscope.domain.quant.discovery;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockDiscoveryCandidate {
    private Long id;
    private Long runId;
    private String instrumentCode;
    private String name;
    private double price;
    private double lotCost;
    private boolean admitted;
    private String rejectionReasonsJson;
    private String sectorCodesJson;
    private String sectorNamesJson;
    private Double lightweightScore;
    private Integer lightweightRank;
    private Double deepScore;
    private Integer finalRank;
    private String conclusion;
    private Double calibratedProbability;
    private String healthStatus;
    private String detailJson;
    private LocalDate asOfDate;
    private int horizonDays;
    private String maturityStatus;
    private LocalDate entryDate;
    private LocalDate exitDate;
    private Double entryOpen;
    private Double exitOpen;
    private Double actualNetReturn;
    private String actualDirection;
    private Boolean predictionCorrect;
    private LocalDateTime settledAt;
    private String outcomeSourceCode;
    private String outcomeNote;
}
