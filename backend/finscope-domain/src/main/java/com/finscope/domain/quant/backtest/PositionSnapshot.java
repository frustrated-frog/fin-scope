package com.finscope.domain.quant.backtest;

import java.time.LocalDate;

public class PositionSnapshot {
    private LocalDate tradeDate; private String instrumentCode; private long quantity; private double price; private double marketValue; private double weight;
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getMarketValue() { return marketValue; }
    public void setMarketValue(double marketValue) { this.marketValue = marketValue; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
}
