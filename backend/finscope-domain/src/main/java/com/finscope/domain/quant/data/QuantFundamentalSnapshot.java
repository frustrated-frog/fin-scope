package com.finscope.domain.quant.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class QuantFundamentalSnapshot {
    private Long id;
    private Long datasetId;
    private String instrumentCode;
    private LocalDate reportPeriod;
    private LocalDate disclosedAt;
    private BigDecimal pe;
    private BigDecimal pb;
    private BigDecimal marketCap;
    private BigDecimal roe;
    private BigDecimal revenueGrowth;
    private BigDecimal profitGrowth;
    private BigDecimal debtRatio;
}
