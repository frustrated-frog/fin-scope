package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.EquityPoint;
import java.util.ArrayList;
import java.util.List;

public class PerformanceMetrics {
    private static final double TRADING_DAYS = 242d;
    public BacktestMetrics calculate(List<EquityPoint> curve, double riskFreeRate, double turnover) {
        BacktestMetrics result = new BacktestMetrics();
        if (curve == null || curve.isEmpty()) return result;
        double first = curve.get(0).getPortfolioNav(); double last = curve.get(curve.size() - 1).getPortfolioNav();
        result.setTotalReturn(safe(first == 0 ? 0 : last / first - 1d));
        int periods = Math.max(1, curve.size() - 1);
        result.setAnnualizedReturn(safe(first <= 0 || last <= 0 ? 0 : Math.pow(last / first, TRADING_DAYS / periods) - 1d));
        List<Double> returns = new ArrayList<Double>(); int wins = 0; double peak = Double.NEGATIVE_INFINITY, maxDrawdown = 0;
        for (int i = 0; i < curve.size(); i++) {
            double nav = curve.get(i).getPortfolioNav(); peak = Math.max(peak, nav);
            double drawdown = peak <= 0 ? 0 : (peak - nav) / peak; curve.get(i).setDrawdown(drawdown); maxDrawdown = Math.max(maxDrawdown, drawdown);
            if (i > 0) { double previous = curve.get(i - 1).getPortfolioNav(); double daily = previous == 0 ? 0 : nav / previous - 1d; returns.add(daily); if (daily > 0) wins++; }
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0); double variance = 0;
        for (double value : returns) variance += (value - mean) * (value - mean);
        double dailyStd = returns.size() < 2 ? 0 : Math.sqrt(variance / (returns.size() - 1));
        result.setAnnualizedVolatility(safe(dailyStd * Math.sqrt(TRADING_DAYS)));
        result.setSharpeRatio(safe(dailyStd == 0 ? 0 : (mean - riskFreeRate / TRADING_DAYS) / dailyStd * Math.sqrt(TRADING_DAYS)));
        result.setMaxDrawdown(maxDrawdown); result.setCalmarRatio(safe(maxDrawdown == 0 ? 0 : result.getAnnualizedReturn() / maxDrawdown));
        result.setWinRate(returns.isEmpty() ? 0 : (double) wins / returns.size()); result.setTurnover(safe(turnover)); return result;
    }
    private double safe(double value) { return Double.isFinite(value) ? value : 0d; }
}
