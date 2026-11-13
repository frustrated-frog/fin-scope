package com.finscope.domain.fundamentals;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FundamentalMetricChange {
    private String conceptCode;
    private String label;
    private String unit;
    private BigDecimal baseline;
    private BigDecimal current;
    private BigDecimal change;
}
