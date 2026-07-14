package com.finscope.domain.quant.backtest;

import java.time.LocalDate;

public class BacktestTrade {
    /**
     * 信号日期。
     */
    private LocalDate signalDate;
    /**
     * 交易日期。
     */
    private LocalDate tradeDate;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 交易方向。
     */
    private String side;
    /**
     * 持仓数量。
     */
    private long quantity;
    /**
     * 最新价格。
     */
    private double price;
    /**
     * 交易名义金额。
     */
    private double notional;
    /**
     * 交易费用。
     */
    private double fee;
    /**
     * 原因说明。
     */
    private String reason;
    public LocalDate getSignalDate() { return signalDate; }
    public void setSignalDate(LocalDate signalDate) { this.signalDate = signalDate; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getNotional() { return notional; }
    public void setNotional(double notional) { this.notional = notional; }
    public double getFee() { return fee; }
    public void setFee(double fee) { this.fee = fee; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
