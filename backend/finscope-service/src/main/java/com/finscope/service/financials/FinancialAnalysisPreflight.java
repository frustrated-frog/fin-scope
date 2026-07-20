package com.finscope.service.financials;

import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialReportView;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class FinancialAnalysisPreflight {
    private final FinancialAnalysisEngine engine;
    private final FinancialReportRepository reports;

    public FinancialAnalysisPreflight(FinancialAnalysisEngine engine,
                                      FinancialReportRepository reports) {
        this.engine = engine;
        this.reports = reports;
    }

    public boolean requiresRefresh(FinancialReportView view) {
        if (view == null || view.getMetrics() == null || view.getMetrics().isEmpty()) return true;
        for (FinancialMetric metric : view.getMetrics()) {
            if (!FinancialAnalysisEngine.FORMULA_VERSION.equals(metric.getFormulaVersion())) {
                return true;
            }
        }
        return false;
    }

    public FinancialReportView ensureCurrent(FinancialReportView current,
                                             List<FinancialReportView> comparables) {
        if (!requiresRefresh(current)) return current;
        FinancialReportView prior = findPrior(current, comparables);
        FinancialAnalysisResult analyzed = engine.analyze(lines(current), lines(prior));
        Long reportId = current.getReport().getId();
        for (FinancialMetric metric : analyzed.getMetrics()) metric.setReportId(reportId);
        for (FinancialFinding finding : analyzed.getFindings()) finding.setReportId(reportId);
        reports.replaceAnalysis(reportId, analyzed.getMetrics(), analyzed.getFindings());
        current.setMetrics(analyzed.getMetrics());
        current.setFindings(analyzed.getFindings());
        current.setDataGaps(analyzed.getDataGaps());
        return current;
    }

    private FinancialReportView findPrior(FinancialReportView current,
                                          List<FinancialReportView> comparables) {
        if (current == null || current.getReport() == null || comparables == null) return null;
        LocalDate expected = current.getReport().getPeriodEnd().minusYears(1);
        for (FinancialReportView candidate : comparables) {
            if (candidate != null && candidate.getReport() != null
                    && expected.equals(candidate.getReport().getPeriodEnd())
                    && current.getReport().getReportType() == candidate.getReport().getReportType()
                    && same(current.getReport().getScope(), candidate.getReport().getScope())) {
                return candidate;
            }
        }
        return null;
    }

    private List<FinancialLineItem> lines(FinancialReportView view) {
        if (view == null || view.getStatements() == null) return Collections.emptyList();
        List<FinancialLineItem> result = new ArrayList<FinancialLineItem>();
        view.getStatements().values().forEach(result::addAll);
        return result;
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
