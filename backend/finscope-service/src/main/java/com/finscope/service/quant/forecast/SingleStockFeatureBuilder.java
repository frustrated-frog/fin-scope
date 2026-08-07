package com.finscope.service.quant.forecast;

import com.finscope.domain.quant.data.QuantDailyBar;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SingleStockFeatureBuilder {
    static final int FEATURE_COUNT = 7;
    static final int WARMUP_DAYS = 60;
    static final int HORIZON_DAYS = 20;

    List<ForecastSample> build(List<QuantDailyBar> input, double transactionCostRate) {
        List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>(input);
        bars.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        List<ForecastSample> samples = new ArrayList<ForecastSample>();
        for (int signal = WARMUP_DAYS; signal + HORIZON_DAYS < bars.size(); signal++) {
            QuantDailyBar entry = bars.get(signal + 1);
            QuantDailyBar exit = bars.get(signal + HORIZON_DAYS);
            double netReturn = price(exit) / decimal(entry.getOpen()) - 1d - transactionCostRate;
            samples.add(new ForecastSample(
                    bars.get(signal).getTradeDate(), entry.getTradeDate(), exit.getTradeDate(),
                    features(bars, signal), netReturn));
        }
        return samples;
    }

    double[] currentFeatures(List<QuantDailyBar> input) {
        List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>(input);
        bars.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        if (bars.size() <= WARMUP_DAYS) throw new IllegalArgumentException("至少需要 61 根日线计算预测特征");
        return features(bars, bars.size() - 1);
    }

    private double[] features(List<QuantDailyBar> bars, int index) {
        double close = price(bars.get(index));
        return new double[] {
                close / price(bars.get(index - 5)) - 1d,
                close / price(bars.get(index - 20)) - 1d,
                close / price(bars.get(index - 60)) - 1d,
                close / averagePrice(bars, index - 19, index) - 1d,
                close / averagePrice(bars, index - 59, index) - 1d,
                volatility(bars, index - 19, index),
                averageAmount(bars, index - 19, index) / averageAmount(bars, index - 59, index) - 1d
        };
    }

    private double averagePrice(List<QuantDailyBar> bars, int from, int to) {
        double sum = 0d;
        for (int i = from; i <= to; i++) sum += price(bars.get(i));
        return sum / (to - from + 1);
    }

    private double averageAmount(List<QuantDailyBar> bars, int from, int to) {
        double sum = 0d;
        for (int i = from; i <= to; i++) sum += decimal(bars.get(i).getAmount());
        return sum / (to - from + 1);
    }

    private double volatility(List<QuantDailyBar> bars, int from, int to) {
        int count = to - from + 1;
        double[] returns = new double[count];
        double mean = 0d;
        for (int i = 0; i < count; i++) {
            returns[i] = Math.log(price(bars.get(from + i)) / price(bars.get(from + i - 1)));
            mean += returns[i];
        }
        mean /= count;
        double variance = 0d;
        for (double value : returns) variance += (value - mean) * (value - mean);
        return Math.sqrt(variance / Math.max(1, count - 1)) * Math.sqrt(252d);
    }

    private double price(QuantDailyBar bar) {
        return decimal(bar.getAdjustedClose() == null ? bar.getClose() : bar.getAdjustedClose());
    }

    private double decimal(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("日线价格和成交额必须为正数");
        return value.doubleValue();
    }
}
