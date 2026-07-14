package com.finscope.domain.quant.backtest;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PositionSnapshot {
    /**
     * 交易日期。
     */
    private LocalDate tradeDate;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 持仓数量。
     */
    private long quantity;
    /**
     * 最新价格。
     */
    private double price;
    /**
     * 市值。
     */
    private double marketValue;
    /**
     * 权重。
     */
    private double weight;
}
