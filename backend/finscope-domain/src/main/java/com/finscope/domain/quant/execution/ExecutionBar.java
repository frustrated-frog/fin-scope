package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class ExecutionBar {
    private java.time.LocalDate tradeDate;
    private String instrumentCode;
    private java.math.BigDecimal open;
    private java.math.BigDecimal close;
    private com.finscope.common.enums.quant.OpenExecutionState openState;
    private String sourceEvidence;
}
