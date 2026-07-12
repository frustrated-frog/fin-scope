package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestTrade;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PortfolioLedger {
    private double cash;
    private final double initialCapital;
    private final Map<String, Long> positions = new LinkedHashMap<String, Long>();
    private final Map<String, Double> lastClose = new LinkedHashMap<String, Double>();
    private final List<BacktestTrade> trades = new ArrayList<BacktestTrade>();
    private double tradedNotional;

    PortfolioLedger(double initialCapital) { this.cash = initialCapital; this.initialCapital = initialCapital; }

    void rebalance(LocalDate signalDate, LocalDate tradeDate, Map<String, Double> targets,
                   Map<String, QuantDailyBar> bars, QuantStrategySpec spec, List<String> warnings) {
        double equity = totalAsset(bars, true, warnings, tradeDate); Map<String, Long> desired = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, Double> target : targets.entrySet()) {
            QuantDailyBar bar = bars.get(target.getKey()); if (!tradable(bar)) continue;
            double buyPrice = fillPrice(bar.getOpen().doubleValue(), true, spec.getExecution().getSlippageBps());
            long shares = (long) Math.floor(equity * target.getValue() / buyPrice / 100d) * 100L;
            desired.put(target.getKey(), shares);
        }
        List<String> codes = new ArrayList<String>(positions.keySet()); java.util.Collections.sort(codes);
        for (String code : codes) {
            long current = positions.get(code); long target = desired.containsKey(code) ? desired.get(code) : 0L;
            if (current > target) sell(signalDate, tradeDate, code, current - target, bars.get(code), spec, warnings);
        }
        codes = new ArrayList<String>(desired.keySet()); java.util.Collections.sort(codes);
        for (String code : codes) {
            long current = positions.containsKey(code) ? positions.get(code) : 0L; long target = desired.get(code);
            if (target > current) buy(signalDate, tradeDate, code, target - current, bars.get(code), spec, warnings);
        }
    }

    void rememberClose(Map<String, QuantDailyBar> bars) {
        for (QuantDailyBar bar : bars.values()) {
            if (bar.getClose() != null && bar.getClose().doubleValue() > 0) lastClose.put(bar.getInstrumentCode(), bar.getClose().doubleValue());
        }
    }

    double totalAsset(Map<String, QuantDailyBar> bars, boolean useOpen, List<String> warnings, LocalDate date) {
        double total = cash;
        for (Map.Entry<String, Long> position : positions.entrySet()) {
            QuantDailyBar bar = bars.get(position.getKey());
            Double price = bar == null ? lastClose.get(position.getKey())
                    : (useOpen ? bar.getOpen().doubleValue() : bar.getClose().doubleValue());
            if (price == null) throw new IllegalStateException(date + " " + position.getKey() + " 缺少可用估值价格");
            if (bar == null) addWarning(warnings, date + " " + position.getKey() + " 缺少行情，沿用上一有效收盘价");
            total += position.getValue() * price;
        }
        return total;
    }
    double getCash() { return cash; }
    List<BacktestTrade> getTrades() { return trades; }
    double turnover() { return initialCapital == 0 ? 0 : tradedNotional / initialCapital; }

    private void sell(LocalDate signal, LocalDate date, String code, long quantity, QuantDailyBar bar,
                      QuantStrategySpec spec, List<String> warnings) {
        if (!tradable(bar) || bar.isLimitDown()) { warnings.add(date + " " + code + " 无法卖出"); return; }
        double price = fillPrice(bar.getOpen().doubleValue(), false, spec.getExecution().getSlippageBps());
        double notional = price * quantity; double fee = commission(notional, spec.getCost().getSellCommission(),
                spec.getCost().getMinimumCommission()) + notional * spec.getCost().getStampDuty();
        cash += notional - fee; positions.put(code, positions.get(code) - quantity); if (positions.get(code) == 0) positions.remove(code);
        record(signal, date, code, "SELL", quantity, price, notional, fee); tradedNotional += notional;
    }

    private void buy(LocalDate signal, LocalDate date, String code, long requested, QuantDailyBar bar,
                     QuantStrategySpec spec, List<String> warnings) {
        if (!tradable(bar) || bar.isLimitUp()) { warnings.add(date + " " + code + " 无法买入"); return; }
        double price = fillPrice(bar.getOpen().doubleValue(), true, spec.getExecution().getSlippageBps()); long quantity = requested;
        while (quantity >= 100) {
            double notional = price * quantity; double fee = commission(notional, spec.getCost().getBuyCommission(), spec.getCost().getMinimumCommission());
            if (notional + fee <= cash + 0.000001) { cash -= notional + fee; positions.put(code, positions.getOrDefault(code, 0L) + quantity);
                record(signal, date, code, "BUY", quantity, price, notional, fee); tradedNotional += notional; return; }
            quantity -= 100;
        }
        warnings.add(date + " " + code + " 现金不足，未买入");
    }

    private boolean tradable(QuantDailyBar bar) { return bar != null && "TRADING".equals(bar.getTradeStatus()); }
    private double fillPrice(double open, boolean buy, double bps) { return open * (1d + (buy ? 1 : -1) * bps / 10000d); }
    private double commission(double notional, double rate, double minimum) { return rate == 0 && minimum == 0 ? 0 : Math.max(minimum, notional * rate); }
    private void record(LocalDate signal, LocalDate date, String code, String side, long quantity, double price, double notional, double fee) {
        BacktestTrade trade = new BacktestTrade(); trade.setSignalDate(signal); trade.setTradeDate(date); trade.setInstrumentCode(code);
        trade.setSide(side); trade.setQuantity(quantity); trade.setPrice(price); trade.setNotional(notional); trade.setFee(fee); trade.setReason("REBALANCE"); trades.add(trade);
    }
    private void addWarning(List<String> warnings, String value) { if (!warnings.contains(value)) warnings.add(value); }
}
