package com.finscope.domain.quant.backtest;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PositionSnapshot {
    private LocalDate tradeDate;
    private String instrumentCode;
    private long quantity;
    private double price;
    private double marketValue;
    private double weight;
}
