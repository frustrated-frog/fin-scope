package com.finscope.domain.marketpulse;

import lombok.Data;

/** 市场参与度的平滑动量与冲击状态。 */
@Data
public class MarketBreadthMomentum {
    private Double mcclellanOscillator;
    private Double breadthThrustRatio;
    private String status;
}
