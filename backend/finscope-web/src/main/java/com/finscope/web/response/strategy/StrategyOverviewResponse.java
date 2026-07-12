package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.StrategyHolding;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class StrategyOverviewResponse {
    private List<StrategyHoldingResponse> holdings;
    private double targetWeight;
    private double currentWeight;

    public static StrategyOverviewResponse of(List<StrategyHolding> values) {
        StrategyOverviewResponse response = new StrategyOverviewResponse();
        response.holdings = values.stream()
                .map(StrategyHoldingResponse::of)
                .collect(Collectors.toList());
        response.targetWeight = values.stream()
                .mapToDouble(StrategyHolding::getTargetWeight)
                .sum();
        response.currentWeight = values.stream()
                .mapToDouble(StrategyHolding::getCurrentWeight)
                .sum();
        return response;
    }
}
