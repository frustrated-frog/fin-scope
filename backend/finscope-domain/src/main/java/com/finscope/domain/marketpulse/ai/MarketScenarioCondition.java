package com.finscope.domain.marketpulse.ai;

import com.finscope.common.enums.marketpulse.*;
import lombok.Data;

@Data
public class MarketScenarioCondition {
    private MarketScenarioMetric metric;
    private MarketScenarioDirection direction;
    private MarketAiPoint explanation;
}
