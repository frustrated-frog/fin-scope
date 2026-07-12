package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.EquityPoint;
import com.finscope.domain.quant.backtest.AnnualPerformance;
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
    public List<AnnualPerformance> annual(List<EquityPoint> curve) {
        List<AnnualPerformance> result = new ArrayList<AnnualPerformance>();
        if (curve == null || curve.isEmpty()) return result;
        int cursor = 0;
        while (cursor < curve.size()) {
            int year = curve.get(cursor).getTradeDate().getYear(), end = cursor;
            while (end + 1 < curve.size() && curve.get(end + 1).getTradeDate().getYear() == year) end++;
            EquityPoint before = cursor == 0 ? curve.get(cursor) : curve.get(cursor - 1); EquityPoint last = curve.get(end);
            AnnualPerformance value = new AnnualPerformance(); value.setYear(year);
            value.setPortfolioReturn(ratio(last.getPortfolioNav(), before.getPortfolioNav()));
            value.setBenchmarkReturn(ratio(last.getBenchmarkNav(), before.getBenchmarkNav()));
            value.setExcessReturn(value.getPortfolioReturn() - value.getBenchmarkReturn());
            double peak = before.getPortfolioNav(), drawdown = 0;
            for (int i = cursor; i <= end; i++) { peak = Math.max(peak, curve.get(i).getPortfolioNav()); drawdown = Math.max(drawdown, peak <= 0 ? 0 : (peak - curve.get(i).getPortfolioNav()) / peak); }
            value.setMaxDrawdown(drawdown); result.add(value); cursor = end + 1;
        }
        return result;
    }
    private double ratio(double last, double first) { return first <= 0 ? 0 : last / first - 1d; }
    private double safe(double value) { return Double.isFinite(value) ? value : 0d; }
}
