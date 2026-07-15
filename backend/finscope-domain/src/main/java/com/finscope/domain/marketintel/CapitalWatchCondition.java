package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CapitalWatchCondition {
    private String id;
    private String label;
    private String factorRef;
    private String operator;
    private BigDecimal threshold;
    private String unit;
}
