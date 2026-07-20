package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialMetric;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FinancialAnalysisResult {
    private List<FinancialMetric> metrics = new ArrayList<FinancialMetric>();
    private List<FinancialFinding> findings = new ArrayList<FinancialFinding>();
    private List<String> dataGaps = new ArrayList<String>();
}
