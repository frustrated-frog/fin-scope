package com.finscope.service.quant.factor;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FactorCalculator {
    public double value(String code, List<QuantDailyBar> history, QuantFundamentalSnapshot fundamental) {
        if ("MOMENTUM_20D".equals(code)) return returnOver(history, 20);
        if ("MOMENTUM_60D".equals(code)) return returnOver(history, 60);
        if ("REVERSAL_5D".equals(code)) return -returnOver(history, 5);
        if ("VOLATILITY_20D".equals(code)) return -volatility(history, 20);
        if ("AVG_AMOUNT_20D".equals(code)) return Math.log(Math.max(1d, averageAmount(history, 20)));
        if ("TURNOVER_PROXY_20D".equals(code)) return -volumeVariation(history, 20);
        if (fundamental == null) return Double.NaN;
        if ("LOG_MARKET_CAP".equals(code)) return log(fundamental.getMarketCap());
        if ("EP".equals(code)) return reciprocal(fundamental.getPe());
        if ("BP".equals(code)) return reciprocal(fundamental.getPb());
        if ("ROE".equals(code)) return number(fundamental.getRoe());
        if ("LOW_DEBT".equals(code)) return -number(fundamental.getDebtRatio());
        if ("REVENUE_GROWTH".equals(code)) return number(fundamental.getRevenueGrowth());
        if ("PROFIT_GROWTH".equals(code)) return number(fundamental.getProfitGrowth());
        return Double.NaN;
    }

    private double returnOver(List<QuantDailyBar> values, int days) {
        if (values == null || values.size() <= days) return Double.NaN;
        double current = values.get(values.size() - 1).getAdjustedClose().doubleValue();
        double previous = values.get(values.size() - 1 - days).getAdjustedClose().doubleValue();
        return previous == 0 ? Double.NaN : current / previous - 1d;
    }

    private double volatility(List<QuantDailyBar> values, int days) {
        if (values == null || values.size() <= days) return Double.NaN;
        List<Double> returns = new ArrayList<Double>();
        for (int i = values.size() - days; i < values.size(); i++) {
            double before = values.get(i - 1).getAdjustedClose().doubleValue();
            double current = values.get(i).getAdjustedClose().doubleValue();
            returns.add(current / before - 1d);
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sum = 0; for (double item : returns) sum += (item - mean) * (item - mean);
        return Math.sqrt(sum / Math.max(1, returns.size() - 1));
    }

    private double averageAmount(List<QuantDailyBar> values, int days) {
        if (values == null || values.size() < days) return Double.NaN;
        double sum = 0; for (int i = values.size() - days; i < values.size(); i++) sum += values.get(i).getAmount().doubleValue();
        return sum / days;
    }

    private double volumeVariation(List<QuantDailyBar> values, int days) {
        if (values == null || values.size() < days) return Double.NaN;
        double mean = 0; for (int i = values.size() - days; i < values.size(); i++) mean += values.get(i).getVolume().doubleValue();
        mean /= days; if (mean == 0) return Double.NaN;
        double sum = 0; for (int i = values.size() - days; i < values.size(); i++) {
            double delta = values.get(i).getVolume().doubleValue() - mean; sum += delta * delta;
        }
        return Math.sqrt(sum / days) / mean;
    }

    private double reciprocal(java.math.BigDecimal value) { double number = number(value); return number == 0 ? Double.NaN : 1d / number; }
    private double log(java.math.BigDecimal value) { double number = number(value); return number <= 0 ? Double.NaN : Math.log(number); }
    private double number(java.math.BigDecimal value) { return value == null ? Double.NaN : value.doubleValue(); }
}
