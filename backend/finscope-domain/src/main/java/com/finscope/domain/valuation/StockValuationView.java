package com.finscope.domain.valuation;

import com.finscope.domain.instrument.Instrument;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StockValuationView {
    private Instrument instrument;
    private StockValuationSnapshot latest;
    private List<ValuationMetricSummary> metrics = new ArrayList<ValuationMetricSummary>();
    private List<StockValuationSnapshot> history = new ArrayList<StockValuationSnapshot>();
    private List<StockCorporateAction> corporateActions = new ArrayList<StockCorporateAction>();
    private List<String> warnings = new ArrayList<String>();
}
