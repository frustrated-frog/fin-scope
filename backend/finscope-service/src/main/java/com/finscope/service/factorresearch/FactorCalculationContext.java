package com.finscope.service.factorresearch;

import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class FactorCalculationContext {
    private final String datasetId;
    private final String instrumentCode;
    private final LocalDate tradeDate;
    private final LocalDateTime availableAt;
    private final List<QuantDailyBar> history;
    private final QuantFundamentalSnapshot fundamental;
    private final QuantCapitalFlowDaily capitalFlow;

    public FactorCalculationContext(String datasetId, String instrumentCode, LocalDate tradeDate,
                                    LocalDateTime availableAt, List<QuantDailyBar> history,
                                    QuantFundamentalSnapshot fundamental,
                                    QuantCapitalFlowDaily capitalFlow) {
        if (datasetId == null || datasetId.trim().isEmpty() || instrumentCode == null
                || tradeDate == null || availableAt == null) {
            throw new IllegalArgumentException("factor calculation context is incomplete");
        }
        this.datasetId = datasetId;
        this.instrumentCode = instrumentCode;
        this.tradeDate = tradeDate;
        this.availableAt = availableAt;
        this.history = history == null ? Collections.<QuantDailyBar>emptyList() : history;
        this.fundamental = fundamental;
        this.capitalFlow = capitalFlow;
    }

    public String getDatasetId() { return datasetId; }
    public String getInstrumentCode() { return instrumentCode; }
    public LocalDate getTradeDate() { return tradeDate; }
    public LocalDateTime getAvailableAt() { return availableAt; }
    public List<QuantDailyBar> getHistory() { return history; }
    public QuantFundamentalSnapshot getFundamental() { return fundamental; }
    public QuantCapitalFlowDaily getCapitalFlow() { return capitalFlow; }
}
