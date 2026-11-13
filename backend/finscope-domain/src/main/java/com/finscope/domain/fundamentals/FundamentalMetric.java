package com.finscope.domain.fundamentals;

import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class FundamentalMetric {
    private String label;
    private BigDecimal value;
    private String unit;
    private String conceptCode;
    private Long reportId;
    private LocalDate periodEnd;
}
