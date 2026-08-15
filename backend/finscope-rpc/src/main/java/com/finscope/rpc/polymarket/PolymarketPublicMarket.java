package com.finscope.rpc.polymarket;

import lombok.Data;

@Data
public class PolymarketPublicMarket {
    private String marketId;
    private String question;
    private String marketUrl;
    private String yesTokenId;
    private Integer yesProbability;
    private Double oneHourPriceChange;
    private Double oneDayPriceChange;
    private Double volume;
    private Double volume24h;
    private Double openInterest;
    private String endDate;
}
