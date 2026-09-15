package com.finscope.web.response.quant;

import com.finscope.domain.quant.execution.*;
import lombok.Data;

@Data
public class ExecutableReplayResponse {
    private String inputFingerprint;
    private String engineVersion;
    private ExecutableReplayInput frozenInput;
    private java.util.Map<java.time.LocalDate, java.util.Map<String, Double>> targetWeights;
    private TradingProtocol protocol;
    private java.util.List<FrozenSignalBatch> signals;
    private com.finscope.domain.quant.backtest.BacktestResult account;
    private java.util.List<OrderAudit> orders;

    public static ExecutableReplayResponse of(ExecutableReplayReport report) {
        ExecutableReplayResponse response = new ExecutableReplayResponse();
        response.inputFingerprint = report.getInputFingerprint();
        response.engineVersion = report.getEngineVersion();
        response.frozenInput = report.getFrozenInput();
        response.targetWeights = report.getTargetWeights();
        response.protocol = report.getProtocol();
        response.signals = report.getSignals();
        response.account = report.getAccount();
        response.orders = report.getOrders();
        return response;
    }
}
