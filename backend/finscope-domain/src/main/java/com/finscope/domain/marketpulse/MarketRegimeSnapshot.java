package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketLiquidityState;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketRiskAppetiteState;
import com.finscope.common.enums.marketpulse.MarketRotationState;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.enums.marketpulse.MarketTrendState;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MarketRegimeSnapshot {
    private Long id;
    private LocalDate businessDate;
    private MarketTrendState trendState;
    private MarketLiquidityState liquidityState;
    private MarketRiskAppetiteState riskAppetiteState;
    private MarketRotationState rotationState;
    private MarketStage marketStage;
    private int confidenceScore;
    private MarketRegimeFeatures features;
    private List<String> evidence = new ArrayList<>();
    private String explanation;
    private MarketPulseQualityStatus qualityStatus;
    private String sourceFingerprint;
    private LocalDateTime calculatedAt;
}
