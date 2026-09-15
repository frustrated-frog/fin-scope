package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class ExecutableReplayInput {
    private TradingProtocol protocol;
    private java.util.List<java.time.LocalDate> tradingDates;
    private String calendarEvidence;
    private String priceBasis;
    private String corporateActionEvidence;
    private Boolean hasCorporateActions;
    private java.util.List<FrozenSignalBatch> signals;
    private java.util.List<ExecutionBar> bars;
}
