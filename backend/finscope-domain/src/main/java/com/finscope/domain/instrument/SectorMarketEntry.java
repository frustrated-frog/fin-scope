package com.finscope.domain.instrument;

import lombok.Data;

import java.time.LocalDateTime;

/** 同一批次板块目录快照中的一个板块行情。 */
@Data
public class SectorMarketEntry {
    private String code;
    private String name;
    private SectorCategory category;
    private Double price;
    private Double changeAmount;
    private Double changePct;
    private Double turnover;
    private String leaderStockCode;
    private String leaderStockName;
    private Double leaderStockChangePct;
    private LocalDateTime quoteTime;
}
