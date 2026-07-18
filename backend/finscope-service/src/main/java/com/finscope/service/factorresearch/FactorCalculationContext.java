package com.finscope.service.factorresearch;

import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public final class FactorCalculationContext {
    private final String datasetId;
    private final String instrumentCode;
    private final LocalDate tradeDate;
    private final LocalDateTime availableAt;
    private final List<QuantDailyBar> history;
    private final QuantFundamentalSnapshot fundamental;
    private final QuantCapitalFlowDaily capitalFlow;
    private final List<QuantCapitalFlowDaily> capitalHistory;

    public FactorCalculationContext(String datasetId, String instrumentCode, LocalDate tradeDate,
                                    LocalDateTime availableAt, List<QuantDailyBar> history,
                                    QuantFundamentalSnapshot fundamental,
                                    QuantCapitalFlowDaily capitalFlow) {
        this(datasetId, instrumentCode, tradeDate, availableAt, history, fundamental, capitalFlow,
                capitalFlow == null ? Collections.<QuantCapitalFlowDaily>emptyList() : Collections.singletonList(capitalFlow));
    }

    public FactorCalculationContext(String datasetId, String instrumentCode, LocalDate tradeDate,
                                    LocalDateTime availableAt, List<QuantDailyBar> history,
                                    QuantFundamentalSnapshot fundamental, QuantCapitalFlowDaily capitalFlow,
                                    List<QuantCapitalFlowDaily> capitalHistory) {
        if (datasetId == null || datasetId.trim().isEmpty() || instrumentCode == null
                || tradeDate == null || availableAt == null) {
            throw new IllegalArgumentException("factor calculation context is incomplete");
        }
        this.datasetId = datasetId;
        this.instrumentCode = instrumentCode;
        this.tradeDate = tradeDate;
        this.availableAt = availableAt;
        this.history = history == null ? Collections.<QuantDailyBar>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<QuantDailyBar>(history));
        this.fundamental = fundamental;
        this.capitalFlow = capitalFlow;
        this.capitalHistory = capitalHistory == null ? Collections.<QuantCapitalFlowDaily>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<QuantCapitalFlowDaily>(capitalHistory));
    }
}
