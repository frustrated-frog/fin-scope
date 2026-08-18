package com.finscope.web.response;

import com.finscope.domain.instrument.SectorMarketEntry;

import java.time.LocalDateTime;

/** 板块目录行情响应，不包含用户关注状态。 */
public final class SectorMarketEntryResponse {
    private String code;
    private String name;
    private String category;
    private Integer sourceRank;
    private Double mainNetInflow;
    private Double price;
    private Double changeAmount;
    private Double changePct;
    private Double turnover;
    private String leaderStockCode;
    private String leaderStockName;
    private Double leaderStockChangePct;
    private LocalDateTime quoteTime;

    public static SectorMarketEntryResponse of(SectorMarketEntry value) {
        SectorMarketEntryResponse response = new SectorMarketEntryResponse();
        response.code = value.getCode();
        response.name = value.getName();
        response.category = value.getCategory() == null ? null : value.getCategory().name();
        response.sourceRank = value.getSourceRank();
        response.mainNetInflow = value.getMainNetInflow();
        response.price = value.getPrice();
        response.changeAmount = value.getChangeAmount();
        response.changePct = value.getChangePct();
        response.turnover = value.getTurnover();
        response.leaderStockCode = value.getLeaderStockCode();
        response.leaderStockName = value.getLeaderStockName();
        response.leaderStockChangePct = value.getLeaderStockChangePct();
        response.quoteTime = value.getQuoteTime();
        return response;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public Integer getSourceRank() { return sourceRank; }
    public Double getMainNetInflow() { return mainNetInflow; }
    public Double getPrice() { return price; }
    public Double getChangeAmount() { return changeAmount; }
    public Double getChangePct() { return changePct; }
    public Double getTurnover() { return turnover; }
    public String getLeaderStockCode() { return leaderStockCode; }
    public String getLeaderStockName() { return leaderStockName; }
    public Double getLeaderStockChangePct() { return leaderStockChangePct; }
    public LocalDateTime getQuoteTime() { return quoteTime; }
}
