package com.finscope.rpc.polymarket;

import lombok.Data;

@Data
public class PolymarketPublicMarket {
    private String marketId;
    private String question;
    private String marketUrl;
    private Integer yesProbability;
    private Double oneDayPriceChange;
    private Double volume;
    private Double openInterest;
    private String endDate;
}
