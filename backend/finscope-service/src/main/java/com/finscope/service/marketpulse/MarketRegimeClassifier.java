package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketLiquidityState;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketRiskAppetiteState;
import com.finscope.common.enums.marketpulse.MarketRotationState;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.enums.marketpulse.MarketTrendState;
import com.finscope.domain.marketpulse.MarketRegimeFeatures;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class MarketRegimeClassifier {
    public static final String RULE_VERSION = "MARKET_REGIME_V1";

    public MarketRegimeSnapshot classify(LocalDate businessDate, MarketRegimeFeatures features,
                                         String sourceFingerprint, LocalDateTime calculatedAt) {
        MarketRegimeSnapshot value = base(businessDate, features, sourceFingerprint, calculatedAt);
        if (!complete(features)) {
            value.setTrendState(MarketTrendState.INSUFFICIENT_DATA);
            value.setLiquidityState(MarketLiquidityState.INSUFFICIENT_DATA);
            value.setRiskAppetiteState(MarketRiskAppetiteState.INSUFFICIENT_DATA);
            value.setRotationState(MarketRotationState.INSUFFICIENT_DATA);
            value.setMarketStage(MarketStage.INSUFFICIENT_DATA);
            value.setConfidenceScore(30);
            value.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
            value.setExplanation("关键行情历史不足，暂不能形成可信市场状态");
            value.getEvidence().add("缺少趋势、波动率、成交额或回撤关键特征");
            return value;
        }
        value.setTrendState(trend(features));
        value.setLiquidityState(liquidity(features));
        value.setRiskAppetiteState(riskAppetite(features, value.getTrendState()));
        value.setRotationState(rotation(features));
        value.setMarketStage(stage(features, value));
        value.setQualityStatus(features.getMarketBreadth() == null
                ? MarketPulseQualityStatus.PARTIAL : MarketPulseQualityStatus.READY);
        value.setConfidenceScore(features.getMarketBreadth() == null ? 72 : 84);
        explain(value, features);
        return value;
    }

    private MarketRegimeSnapshot base(LocalDate businessDate, MarketRegimeFeatures features,
                                      String sourceFingerprint, LocalDateTime calculatedAt) {
        MarketRegimeSnapshot value = new MarketRegimeSnapshot();
        value.setBusinessDate(businessDate);
        value.setFeatures(features);
        value.setSourceFingerprint(RULE_VERSION + ":" + sourceFingerprint);
        value.setCalculatedAt(calculatedAt);
        return value;
    }

    private boolean complete(MarketRegimeFeatures value) {
        return value != null && finite(value.getReturn1d()) && finite(value.getReturn5d())
                && finite(value.getReturn20d()) && finite(value.getPriceVsMa20())
                && finite(value.getVolatility20()) && finite(value.getMaxDrawdown20())
                && finite(value.getAmountRatio5To20()) && finite(value.getSectorDispersion());
    }

    private MarketTrendState trend(MarketRegimeFeatures value) {
        if (value.getReturn20d() >= 0.03D && value.getPriceVsMa20() > 0D) {
            return MarketTrendState.UPTREND;
        }
        if (value.getReturn20d() <= -0.03D && value.getPriceVsMa20() < 0D) {
            return MarketTrendState.DOWNTREND;
        }
        return MarketTrendState.RANGE;
    }

    private MarketLiquidityState liquidity(MarketRegimeFeatures value) {
        if (value.getAmountRatio5To20() >= 1.15D) {
            return MarketLiquidityState.EXPANDING;
        }
        if (value.getAmountRatio5To20() <= 0.85D) {
            return MarketLiquidityState.SHRINKING;
        }
        return MarketLiquidityState.NORMAL;
    }

    private MarketRiskAppetiteState riskAppetite(MarketRegimeFeatures value, MarketTrendState trend) {
        if (value.getVolatility20() >= 0.30D || value.getReturn5d() <= -0.04D) {
            return MarketRiskAppetiteState.LOW;
        }
        if (trend == MarketTrendState.UPTREND && value.getMarketBreadth() != null
                && value.getMarketBreadth() >= 0.58D) {
            return MarketRiskAppetiteState.HIGH;
        }
        return MarketRiskAppetiteState.NEUTRAL;
    }

    private MarketRotationState rotation(MarketRegimeFeatures value) {
        if (value.getSectorDispersion() >= 0.025D
                || (value.getTopSectorTurnover() != null && value.getTopSectorTurnover() >= 0.60D)) {
            return MarketRotationState.FAST;
        }
        if (value.getSectorDispersion() <= 0.012D) {
            return MarketRotationState.SLOW;
        }
        return MarketRotationState.NORMAL;
    }

    private MarketStage stage(MarketRegimeFeatures features, MarketRegimeSnapshot snapshot) {
        if (features.getMaxDrawdown20() <= -0.05D && features.getReturn1d() > 0D
                && snapshot.getLiquidityState() == MarketLiquidityState.SHRINKING) {
            return MarketStage.POST_SELL_OFF_REPAIR;
        }
        if (features.getReturn1d() <= -0.03D || features.getReturn5d() <= -0.06D) {
            return MarketStage.SELL_OFF;
        }
        if (snapshot.getTrendState() == MarketTrendState.UPTREND
                && snapshot.getLiquidityState() == MarketLiquidityState.EXPANDING
                && snapshot.getRiskAppetiteState() == MarketRiskAppetiteState.HIGH) {
            return MarketStage.RISK_ON;
        }
        if (features.getReturn20d() > 0.03D && features.getReturn5d() < 0D) {
            return MarketStage.HIGH_LEVEL_DIVERGENCE;
        }
        return MarketStage.RANGE_ROTATION;
    }

    private void explain(MarketRegimeSnapshot value, MarketRegimeFeatures features) {
        value.getEvidence().add(String.format("20日收益 %.2f%%，价格相对MA20 %.2f%%",
                features.getReturn20d() * 100D, features.getPriceVsMa20() * 100D));
        value.getEvidence().add(String.format("5日成交额相对20日均值 %.0f%%",
                features.getAmountRatio5To20() * 100D));
        value.getEvidence().add(String.format("20日最大回撤 %.2f%%，年化波动率 %.2f%%",
                features.getMaxDrawdown20() * 100D, features.getVolatility20() * 100D));
        if (features.getMarketBreadth() != null) {
            value.getEvidence().add(String.format("全市场上涨比例 %.1f%%",
                    features.getMarketBreadth() * 100D));
        }
        if (value.getMarketStage() == MarketStage.POST_SELL_OFF_REPAIR) {
            value.setExplanation("急跌后的缩量修复：指数回升但成交额仍低于中期均值");
        } else if (value.getMarketStage() == MarketStage.SELL_OFF) {
            value.setExplanation("风险释放：短期跌幅与波动率同步上升");
        } else if (value.getMarketStage() == MarketStage.RISK_ON) {
            value.setExplanation("放量上行：趋势、流动性与市场宽度共同确认");
        } else if (value.getMarketStage() == MarketStage.HIGH_LEVEL_DIVERGENCE) {
            value.setExplanation("高位分歧：中期趋势仍强但短期收益转弱");
        } else {
            value.setExplanation("震荡轮动：趋势与流动性尚未形成同向突破");
        }
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
