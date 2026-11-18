package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiScenario {
    private MarketAiPoint thesis;
    private MarketScenarioCondition confirmation;
    private MarketScenarioCondition invalidation;
}
