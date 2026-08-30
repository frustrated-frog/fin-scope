package com.finscope.domain.marketpulse;

import lombok.Data;

/** 全市场按涨跌方向拆分的成交额压力。 */
@Data
public class MarketVolumePressure {
    private Double advanceAmount;
    private Double declineAmount;
    private Double flatAmount;
    private Double advanceAmountRatio;
    private Double netAdvancingAmount;
    private Double trin;
}
