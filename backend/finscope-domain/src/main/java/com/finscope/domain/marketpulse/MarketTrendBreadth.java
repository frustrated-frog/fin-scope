package com.finscope.domain.marketpulse;

import lombok.Data;

@Data
public class MarketTrendBreadth {
    private Double ma20Ratio;
    private Integer ma20ValidCount;
    private Double ma60Ratio;
    private Integer ma60ValidCount;
    private Double ma120Ratio;
    private Integer ma120ValidCount;
    private Double ma250Ratio;
    private Integer ma250ValidCount;
}
