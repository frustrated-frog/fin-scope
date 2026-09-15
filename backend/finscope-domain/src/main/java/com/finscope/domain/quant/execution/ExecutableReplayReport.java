package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class ExecutableReplayReport {
    private String inputFingerprint;
    private String engineVersion;
    private ExecutableReplayInput frozenInput;
    private java.util.Map<java.time.LocalDate, java.util.Map<String, Double>> targetWeights;
    private TradingProtocol protocol;
    private java.util.List<FrozenSignalBatch> signals;
    private com.finscope.domain.quant.backtest.BacktestResult account;
    private java.util.List<OrderAudit> orders;
}
