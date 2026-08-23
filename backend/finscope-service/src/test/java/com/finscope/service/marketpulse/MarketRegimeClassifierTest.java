package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketLiquidityState;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.enums.marketpulse.MarketTrendState;
import com.finscope.domain.marketpulse.MarketRegimeFeatures;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketRegimeClassifierTest {
    private final MarketRegimeClassifier classifier = new MarketRegimeClassifier();

    @Test
    void classifiesPostSellOffShrinkingRepairFromFrozenFeatures() {
        MarketRegimeFeatures features = complete();
        features.setReturn1d(0.012D);
        features.setReturn5d(-0.025D);
        features.setMaxDrawdown20(-0.08D);
        features.setAmountRatio5To20(0.72D);

        MarketRegimeSnapshot result = classifier.classify(
                LocalDate.of(2026, 8, 21), features, "market-v1",
                LocalDateTime.of(2026, 8, 21, 16, 0));

        assertEquals(MarketStage.POST_SELL_OFF_REPAIR, result.getMarketStage());
        assertEquals(MarketLiquidityState.SHRINKING, result.getLiquidityState());
        assertEquals(MarketTrendState.RANGE, result.getTrendState());
        assertTrue(result.getExplanation().contains("缩量修复"));
        assertTrue(result.getEvidence().stream().anyMatch(value -> value.contains("成交额")));
    }

    @Test
    void marksCriticalMissingFeaturesAsInsufficientInsteadOfNeutral() {
        MarketRegimeFeatures features = new MarketRegimeFeatures();
        features.setReturn1d(0.01D);

        MarketRegimeSnapshot result = classifier.classify(
                LocalDate.of(2026, 8, 21), features, "partial",
                LocalDateTime.of(2026, 8, 21, 16, 0));

        assertEquals(MarketStage.INSUFFICIENT_DATA, result.getMarketStage());
        assertEquals(MarketTrendState.INSUFFICIENT_DATA, result.getTrendState());
        assertEquals(MarketPulseQualityStatus.PARTIAL, result.getQualityStatus());
        assertTrue(result.getConfidenceScore() < 50);
    }

    @Test
    void requiresBroadParticipationBeforeDeclaringHighRiskAppetite() {
        MarketRegimeFeatures broad = complete();
        broad.setReturn20d(0.05D);
        broad.setPriceVsMa20(0.03D);
        broad.setMarketBreadth(0.63D);
        MarketRegimeFeatures narrow = complete();
        narrow.setReturn20d(0.05D);
        narrow.setPriceVsMa20(0.03D);
        narrow.setMarketBreadth(0.35D);

        MarketRegimeSnapshot broadResult = classifier.classify(
                LocalDate.of(2026, 8, 21), broad, "broad",
                LocalDateTime.of(2026, 8, 21, 16, 0));
        MarketRegimeSnapshot narrowResult = classifier.classify(
                LocalDate.of(2026, 8, 21), narrow, "narrow",
                LocalDateTime.of(2026, 8, 21, 16, 0));

        assertEquals(MarketPulseQualityStatus.READY, broadResult.getQualityStatus());
        assertEquals(com.finscope.common.enums.marketpulse.MarketRiskAppetiteState.HIGH,
                broadResult.getRiskAppetiteState());
        assertEquals(com.finscope.common.enums.marketpulse.MarketRiskAppetiteState.NEUTRAL,
                narrowResult.getRiskAppetiteState());
        assertTrue(narrowResult.getEvidence().stream().anyMatch(value -> value.contains("上涨比例")));
    }

    @Test
    void treatsADeepBroadDeclineAsSellOffBeforeTheIndexReachesThreePercent() {
        MarketRegimeFeatures features = complete();
        features.setReturn1d(-0.029D);
        features.setMarketBreadth(0.25D);

        MarketRegimeSnapshot result = classifier.classify(
                LocalDate.of(2026, 8, 19), features, "broad-sell-off",
                LocalDateTime.of(2026, 8, 19, 16, 0));

        assertEquals(MarketStage.SELL_OFF, result.getMarketStage());
    }

    private MarketRegimeFeatures complete() {
        MarketRegimeFeatures value = new MarketRegimeFeatures();
        value.setReturn1d(0.002D);
        value.setReturn5d(0.01D);
        value.setReturn20d(0.015D);
        value.setPriceVsMa20(0.005D);
        value.setPriceVsMa60(0.02D);
        value.setVolatility20(0.21D);
        value.setMaxDrawdown20(-0.03D);
        value.setAmountRatio5To20(1D);
        value.setMarketBreadth(0.52D);
        value.setGrowthRelativeReturn5d(-0.01D);
        value.setSectorDispersion(0.018D);
        value.setTopSectorTurnover(0.42D);
        return value;
    }
}
