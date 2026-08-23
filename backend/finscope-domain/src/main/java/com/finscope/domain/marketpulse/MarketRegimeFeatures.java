package com.finscope.domain.marketpulse;

import lombok.Data;

@Data
public class MarketRegimeFeatures {
    private Double return1d;
    private Double return5d;
    private Double return20d;
    private Double priceVsMa20;
    private Double priceVsMa60;
    private Double volatility20;
    private Double maxDrawdown20;
    private Double amountRatio5To20;
    private Double marketBreadth;
    private Double growthRelativeReturn5d;
    private Double sectorDispersion;
    private Double topSectorTurnover;
}
