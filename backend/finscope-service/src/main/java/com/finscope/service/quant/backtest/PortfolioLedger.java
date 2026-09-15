package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestTrade;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.backtest.PositionSnapshot;

import com.finscope.domain.quant.execution.TradingProtocol;
import com.finscope.domain.quant.execution.OrderAudit;
import com.finscope.common.enums.quant.ReplayOrderReason;
import com.finscope.common.enums.quant.ReplayOrderSide;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PortfolioLedger {
    private final double initialCapital;
    private final Map<String, Long> positions = new LinkedHashMap<String, Long>();
    private final Map<String, Double> lastClose = new LinkedHashMap<String, Double>();
    private final List<BacktestTrade> trades = new ArrayList<BacktestTrade>();
    private final List<OrderAudit> orders = new ArrayList<OrderAudit>();
    private TradingProtocol protocol;
    private Map<String, String> industries;
    private double cash;
    private double tradedNotional;

    PortfolioLedger(double initialCapital) {
        this.cash = initialCapital;
        this.initialCapital = initialCapital;
    }

    PortfolioLedger(TradingProtocol protocol, Map<String, String> industries) {
        this(protocol.getInitialCapital());
        this.protocol = protocol;
        this.industries = industries;
    }

    List<OrderAudit> getOrders() {
        return orders;
    }

    void rebalance(LocalDate signalDate, LocalDate tradeDate, Map<String, Double> targets,
                   Map<String, QuantDailyBar> bars, QuantStrategySpec spec, List<String> warnings) {
        double equity = totalAsset(bars, true, warnings, tradeDate);
        Map<String, Long> desired = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, Double> target : targets.entrySet()) {
            QuantDailyBar bar = bars.get(target.getKey());
            if (bar == null) {
                continue;
            }
            double buyPrice = fillPrice(bar.getOpen().doubleValue(), true, spec.getExecution().getSlippageBps());
            long shares = (long) Math.floor(equity * target.getValue() / buyPrice / 100d) * 100L;
            desired.put(target.getKey(), shares);
        }
        List<String> codes = new ArrayList<String>(positions.keySet());
        java.util.Collections.sort(codes);
        for (String code : codes) {
            long current = positions.get(code);
            long target = desired.containsKey(code) ? desired.get(code) : 0L;
            if (current > target) {
                sell(signalDate, tradeDate, code, current - target, bars.get(code), spec, warnings);
            }
        }
        codes = new ArrayList<String>(desired.keySet());
        java.util.Collections.sort(codes);
        for (String code : codes) {
            long current = positions.containsKey(code) ? positions.get(code) : 0L;
            long target = desired.get(code);
            if (target > current) {
                double budget = purchaseBudget(code, equity, bars);
                buy(signalDate, tradeDate, code, target - current, bars.get(code), spec, warnings, budget);
            } else if (target == 0 && current == 0) {
                audit(signalDate, tradeDate, code, ReplayOrderSide.BUY, 0, 0, 0, ReplayOrderReason.NO_LOT_BUDGET);
            }
        }
    }

    void rememberClose(Map<String, QuantDailyBar> bars) {
        for (QuantDailyBar bar : bars.values()) {
            if (bar.getClose() != null && bar.getClose().doubleValue() > 0) {
                lastClose.put(bar.getInstrumentCode(), bar.getClose().doubleValue());
            }
        }
    }

    double totalAsset(Map<String, QuantDailyBar> bars, boolean useOpen, List<String> warnings, LocalDate date) {
        double total = cash;
        for (Map.Entry<String, Long> position : positions.entrySet()) {
            QuantDailyBar bar = bars.get(position.getKey());
            Double price = bar == null ? lastClose.get(position.getKey())
                    : (useOpen ? bar.getOpen().doubleValue() : bar.getClose().doubleValue());
            if (price == null) {
                throw new IllegalStateException(date + " " + position.getKey() + " 缺少可用估值价格");
            }
            if (bar == null) {
                addWarning(warnings, date + " " + position.getKey() + " 缺少行情，沿用上一有效收盘价");
            }
            total += position.getValue() * price;
        }
        return total;
    }

    double getCash() {
        return cash;
    }

    List<PositionSnapshot> snapshot(LocalDate date, Map<String, QuantDailyBar> bars, double totalAsset) {
        List<PositionSnapshot> result = new ArrayList<PositionSnapshot>();
        for (Map.Entry<String, Long> position : positions.entrySet()) {
            QuantDailyBar bar = bars.get(position.getKey());
            double price = bar == null ? lastClose.get(position.getKey()) : bar.getClose().doubleValue();
            PositionSnapshot value = new PositionSnapshot();
            value.setTradeDate(date);
            value.setInstrumentCode(position.getKey());
            value.setQuantity(position.getValue());
            value.setPrice(price);
            value.setMarketValue(price * position.getValue());
            value.setWeight(totalAsset <= 0 ? 0 : value.getMarketValue() / totalAsset);
            result.add(value);
        }
        return result;
    }

    List<BacktestTrade> getTrades() {
        return trades;
    }

    double turnover() {
        return initialCapital == 0 ? 0 : tradedNotional / initialCapital;
    }

    private void sell(LocalDate signal, LocalDate date, String code, long quantity, QuantDailyBar bar,
                      QuantStrategySpec spec, List<String> warnings) {
        if (!tradable(bar) || bar.isLimitDown()) {
            audit(signal, date, code, ReplayOrderSide.SELL, quantity, 0, 0, ReplayOrderReason.OPEN_BLOCKED);
            warnings.add(date + " " + code + " 无法卖出");
            return;
        }
        double price = fillPrice(bar.getOpen().doubleValue(), false, spec.getExecution().getSlippageBps());
        double notional = price * quantity;
        double fee = commission(notional, spec.getCost().getSellCommission(),
                spec.getCost().getMinimumCommission()) + notional * spec.getCost().getStampDuty();
        cash += notional - fee;
        positions.put(code, positions.get(code) - quantity);
        if (positions.get(code) == 0) {
            positions.remove(code);
        }
        audit(signal, date, code, ReplayOrderSide.SELL, quantity, quantity, fee, ReplayOrderReason.FILLED);
        record(signal, date, code, "SELL", quantity, price, notional, fee);
        tradedNotional += notional;
    }

    private void buy(LocalDate signal, LocalDate date, String code, long requested, QuantDailyBar bar,
                     QuantStrategySpec spec, List<String> warnings, double budget) {
        if (!tradable(bar) || bar.isLimitUp()) {
            audit(signal, date, code, ReplayOrderSide.BUY, requested, 0, 0, ReplayOrderReason.OPEN_BLOCKED);
            warnings.add(date + " " + code + " 无法买入");
            return;
        }
        double price = fillPrice(bar.getOpen().doubleValue(), true, spec.getExecution().getSlippageBps());
        double affordable = Math.min(Math.max(0, cash - spec.getCost().getMinimumCommission()) / price,
                cash / (price * (1 + spec.getCost().getBuyCommission())));
        long quantity = Math.min(requested, (long) (Math.min(affordable, budget / price) / 100) * 100);
        while (quantity >= 100) {
            double notional = price * quantity;
            double fee = commission(notional, spec.getCost().getBuyCommission(), spec.getCost().getMinimumCommission());
            if (notional + fee <= cash + 0.000001) {
                cash -= notional + fee;
                positions.put(code, positions.getOrDefault(code, 0L) + quantity);
                audit(signal, date, code, ReplayOrderSide.BUY, requested, quantity, fee,
                        quantity == requested ? ReplayOrderReason.FILLED : ReplayOrderReason.PARTIAL_BUDGET);
                record(signal, date, code, "BUY", quantity, price, notional, fee);
                tradedNotional += notional;
                return;
            }
            quantity -= 100;
        }
        audit(signal, date, code, ReplayOrderSide.BUY, requested, 0, 0, ReplayOrderReason.NO_LOT_BUDGET);
        warnings.add(date + " " + code + " 现金或仓位预算不足，未买入");
    }

    private double purchaseBudget(String code, double equity, Map<String, QuantDailyBar> bars) {
        if (protocol == null) {
            return cash;
        }
        double total = 0;
        double sector = 0;
        double single = 0;
        for (Map.Entry<String, Long> position : positions.entrySet()) {
            double value = position.getValue() * bars.get(position.getKey()).getOpen().doubleValue();
            total += value;
            if (industries.get(code).equals(industries.get(position.getKey()))) {
                sector += value;
            }
            if (code.equals(position.getKey())) {
                single = value;
            }
        }
        return Math.max(0, Math.min(equity * protocol.getMaxExposure() - total,
                Math.min(equity * protocol.getMaxIndustryWeight() - sector,
                        equity * protocol.getMaxSingleWeight() - single)));
    }

    private void audit(LocalDate signal, LocalDate date, String code, ReplayOrderSide side,
                       long requested, long filled, double fee, ReplayOrderReason reason) {
        OrderAudit order = new OrderAudit();
        order.setSignalDate(signal);
        order.setTradeDate(date);
        order.setInstrumentCode(code);
        order.setSide(side);
        order.setRequestedQuantity(requested);
        order.setFilledQuantity(filled);
        order.setFee(fee);
        order.setReason(reason);
        orders.add(order);
    }

    private boolean tradable(QuantDailyBar bar) {
        return bar != null && "TRADING".equals(bar.getTradeStatus());
    }

    private double fillPrice(double open, boolean buy, double bps) {
        return open * (1d + (buy ? 1 : -1) * bps / 10000d);
    }

    private double commission(double notional, double rate, double minimum) {
        return rate == 0 && minimum == 0 ? 0 : Math.max(minimum, notional * rate);
    }

    private void record(LocalDate signal, LocalDate date, String code, String side, long quantity, double price, double notional, double fee) {
        BacktestTrade trade = new BacktestTrade();
        trade.setSignalDate(signal);
        trade.setTradeDate(date);
        trade.setInstrumentCode(code);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setNotional(notional);
        trade.setFee(fee);
        trade.setReason("REBALANCE");
        trades.add(trade);
    }

    private void addWarning(List<String> warnings, String value) {
        if (!warnings.contains(value)) {
            warnings.add(value);
        }
    }
}
