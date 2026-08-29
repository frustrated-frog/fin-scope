package com.finscope.domain.marketpulse;

import lombok.Data;

@Data
public class MarketReturnDistributionBucket {
    private String code;
    private String label;
    private Double lowerBound;
    private Double upperBound;
    private Integer count;
    private Double ratio;
}
