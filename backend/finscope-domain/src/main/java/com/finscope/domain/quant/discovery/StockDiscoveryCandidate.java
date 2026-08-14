package com.finscope.domain.quant.discovery;

import lombok.Data;

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
}
