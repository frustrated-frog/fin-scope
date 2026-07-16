package com.finscope.service.financials;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.financials.FinancialValueOrigin;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.financials.ExternalFinancialStatements;
import com.finscope.rpc.financials.StructuredFinancialDataGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialRefreshService {
    private final InstrumentRepository instruments;
    private final FinancialReportRepository reports;
    private final StructuredFinancialDataGateway gateway;
    private final FinancialAnalysisEngine analysis;

    public FinancialRefreshService(InstrumentRepository instruments,
                                   FinancialReportRepository reports,
                                   StructuredFinancialDataGateway gateway,
                                   FinancialAnalysisEngine analysis) {
        this.instruments = instruments;
        this.reports = reports;
        this.gateway = gateway;
        this.analysis = analysis;
    }

    @Transactional
    public FinancialReportView refresh(Long instrumentId, LocalDate periodEnd,
                                       FinancialReportType reportType) {
        Instrument instrument = instruments.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("标的不存在：" + instrumentId));
        ExternalFinancialStatements external = gateway.fetch(instrument, periodEnd, reportType);
        FinancialReport report = toReport(instrumentId, external);
        reports.saveReport(report);
        List<FinancialLineItem> lineItems = toLineItems(report.getId(), external);
        reports.replaceLineItems(report.getId(), external.getSourceCode(), lineItems);

        List<FinancialLineItem> prior = reports.findReport(
                        instrumentId, periodEnd.minusYears(1), reportType, external.getScope())
                .map(value -> reports.findAllLineItems(value.getId()))
                .orElseGet(ArrayList<FinancialLineItem>::new);
        FinancialAnalysisResult analyzed = analysis.analyze(lineItems, prior);
        for (FinancialMetric metric : analyzed.getMetrics()) {
            metric.setReportId(report.getId());
        }
        for (FinancialFinding finding : analyzed.getFindings()) {
            finding.setReportId(report.getId());
        }
        reports.replaceAnalysis(report.getId(), analyzed.getMetrics(), analyzed.getFindings());

        FinancialReportView view = new FinancialReportView();
        view.setInstrument(instrument);
        view.setReport(report);
        view.setStatements(group(lineItems));
        view.setMetrics(analyzed.getMetrics());
        view.setFindings(analyzed.getFindings());
        view.setDataGaps(analyzed.getDataGaps());
        return view;
    }

    private FinancialReport toReport(Long instrumentId, ExternalFinancialStatements external) {
        FinancialReport report = new FinancialReport();
        report.setInstrumentId(instrumentId);
        report.setPeriodEnd(external.getPeriodEnd());
        report.setReportType(external.getReportType());
        report.setScope(external.getScope());
        report.setCurrency(external.getCurrency());
        report.setPublishedAt(external.getPublishedAt());
        report.setAudited(external.getAudited());
        report.setQualityStatus(external.getQualityStatus());
        report.setSourceCode(external.getSourceCode());
        report.setWarningMessage(String.join("；", external.getWarnings()));
        return report;
    }

    private List<FinancialLineItem> toLineItems(
            Long reportId, ExternalFinancialStatements external) {
        List<FinancialLineItem> result = new ArrayList<FinancialLineItem>();
        for (ExternalFinancialStatements.Statement statement : external.getStatements()) {
            int order = 0;
            for (ExternalFinancialStatements.Value source : statement.getValues()) {
                FinancialLineItem value = new FinancialLineItem();
                value.setReportId(reportId);
                value.setStatementType(statement.getStatementType());
                value.setSourceLabel(source.getSourceLabel());
                value.setConceptCode(source.getConceptCode());
                value.setPeriodRole(source.getPeriodRole());
                BigDecimal amount = source.getValue();
                value.setNormalizedValue(amount == null ? null
                        : amount.multiply(source.getUnitMultiplier() == null
                        ? BigDecimal.ONE : source.getUnitMultiplier()));
                value.setCurrency(external.getCurrency());
                value.setUnitMultiplier(source.getUnitMultiplier());
                value.setValueOrigin(FinancialValueOrigin.REPORTED);
                value.setSourceField(source.getSourceField());
                value.setSourceCode(external.getSourceCode());
                value.setDisplayOrder(order++);
                value.setQualityStatus(external.getQualityStatus() == null
                        ? FinancialQualityStatus.PARTIAL : external.getQualityStatus());
                result.add(value);
            }
        }
        return result;
    }

    private Map<FinancialStatementType, List<FinancialLineItem>> group(
            List<FinancialLineItem> items) {
        Map<FinancialStatementType, List<FinancialLineItem>> result =
                new EnumMap<FinancialStatementType, List<FinancialLineItem>>(
                        FinancialStatementType.class);
        for (FinancialStatementType type : FinancialStatementType.values()) {
            result.put(type, new ArrayList<FinancialLineItem>());
        }
        for (FinancialLineItem item : items) {
            result.get(item.getStatementType()).add(item);
        }
        return result;
    }
}
