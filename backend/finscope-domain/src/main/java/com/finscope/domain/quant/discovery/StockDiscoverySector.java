package com.finscope.domain.quant.discovery;

import lombok.Data;

@Data
public class StockDiscoverySector {
    private Long id;
    private Long runId;
    private String code;
    private String name;
    private String category;
    private String sourceCode;
    private String sourceFamily;
    private String period;
    private int sourceRank;
    private Double changePct;
    private Double mainNetInflow;
    private Double mainNetInflowRatio;
    private String leaderStockName;
    private String retrievedAt;
}
