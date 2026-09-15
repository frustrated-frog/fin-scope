package com.finscope.web.request.quant;

import com.finscope.domain.quant.execution.*;
import lombok.Data;

@Data
public class RunExecutableReplayRequest {
    private TradingProtocol protocol;
    private java.util.List<java.time.LocalDate> tradingDates;
    private String calendarEvidence;
    private String priceBasis;
    private String corporateActionEvidence;
    private Boolean hasCorporateActions;
    private java.util.List<FrozenSignalBatch> signals;
    private java.util.List<ExecutionBar> bars;

    public ExecutableReplayInput toInput() {
        ExecutableReplayInput input = new ExecutableReplayInput();
        input.setProtocol(protocol);
        input.setTradingDates(tradingDates);
        input.setCalendarEvidence(calendarEvidence);
        input.setPriceBasis(priceBasis);
        input.setCorporateActionEvidence(corporateActionEvidence);
        input.setHasCorporateActions(hasCorporateActions);
        input.setSignals(signals);
        input.setBars(bars);
        return input;
    }
}
