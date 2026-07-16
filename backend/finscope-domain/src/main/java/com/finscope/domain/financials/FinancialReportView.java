package com.finscope.domain.financials;

import com.finscope.domain.instrument.Instrument;
import lombok.Data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Data
public class FinancialReportView {
    private Instrument instrument;
    private FinancialReport report;
    private Map<FinancialStatementType, List<FinancialLineItem>> statements =
            new EnumMap<FinancialStatementType, List<FinancialLineItem>>(FinancialStatementType.class);
    private List<FinancialMetric> metrics = new ArrayList<FinancialMetric>();
    private List<FinancialFinding> findings = new ArrayList<FinancialFinding>();
    private List<String> dataGaps = new ArrayList<String>();
}
