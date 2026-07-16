package com.finscope.service.financials;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class QuarterDerivationEngine {
    public BigDecimal singleQuarter(BigDecimal cumulative, BigDecimal priorCumulative) {
        if (cumulative == null || priorCumulative == null) {
            return null;
        }
        return cumulative.subtract(priorCumulative);
    }
}
