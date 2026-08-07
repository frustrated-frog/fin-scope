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
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FinancialRefreshService {
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(60);
    private final InstrumentRepository instruments;
    private final FinancialReportRepository reports;
    private final StructuredFinancialDataGateway gateway;
    private final FinancialAnalysisEngine analysis;
    private final QuarterDerivationEngine quarterDerivation;

    public FinancialRefreshService(InstrumentRepository instruments,
                                   FinancialReportRepository reports,
                                   StructuredFinancialDataGateway gateway,
                                   FinancialAnalysisEngine analysis,
                                   QuarterDerivationEngine quarterDerivation) {
        this.instruments = instruments;
        this.reports = reports;
        this.gateway = gateway;
        this.analysis = analysis;
        this.quarterDerivation = quarterDerivation;
    }

    public FinancialReportView refresh(Long instrumentId, LocalDate periodEnd,
                                       FinancialReportType reportType) {
        Instrument instrument = instruments.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("标的不存在：" + instrumentId));
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(REFRESH_TIMEOUT)) {
            return refreshWithinDeadline(instrument, periodEnd, reportType);
        }
    }

    private FinancialReportView refreshWithinDeadline(Instrument instrument, LocalDate periodEnd,
                                                       FinancialReportType reportType) {
        ExternalFinancialStatements external = gateway.fetch(instrument, periodEnd, reportType);
        Long instrumentId = instrument.getId();
        FinancialReport report = toReport(instrumentId, external);
        reports.saveReport(report);
        List<FinancialLineItem> lineItems = toLineItems(report.getId(), external);
        List<String> dataGaps = new ArrayList<String>();
        List<FinancialLineItem> priorCumulative = loadPriorCumulative(
                instrument, periodEnd, reportType, external.getScope(), dataGaps);
        lineItems.addAll(deriveSingleQuarter(
                report.getId(), reportType, lineItems, priorCumulative));
        reports.replaceLineItems(report.getId(), external.getSourceCode(), lineItems);

        List<FinancialLineItem> prior = loadReference(
                instrument, periodEnd.minusYears(1), reportType, external.getScope(),
                "缺少上年同期财报，部分同比指标不可计算", dataGaps);
        FinancialAnalysisResult analyzed = analysis.analyze(lineItems, prior);
        analyzed.getDataGaps().addAll(dataGaps);
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

    private List<FinancialLineItem> loadPriorCumulative(
            Instrument instrument, LocalDate periodEnd, FinancialReportType reportType,
            String scope, List<String> dataGaps) {
        if (reportType == FinancialReportType.Q1) {
            return new ArrayList<FinancialLineItem>();
        }
        LocalDate previousPeriod;
        FinancialReportType previousType;
        if (reportType == FinancialReportType.HALF_YEAR) {
            previousPeriod = LocalDate.of(periodEnd.getYear(), 3, 31);
            previousType = FinancialReportType.Q1;
        } else if (reportType == FinancialReportType.Q3) {
            previousPeriod = LocalDate.of(periodEnd.getYear(), 6, 30);
            previousType = FinancialReportType.HALF_YEAR;
        } else {
            previousPeriod = LocalDate.of(periodEnd.getYear(), 9, 30);
            previousType = FinancialReportType.Q3;
        }
        return loadReference(instrument, previousPeriod, previousType, scope,
                "缺少前序累计报告，无法派生单季度值", dataGaps);
    }

    private List<FinancialLineItem> loadReference(
            Instrument instrument, LocalDate periodEnd, FinancialReportType reportType,
            String scope, String gapMessage, List<String> dataGaps) {
        Optional<FinancialReport> local = reports.findReport(
                instrument.getId(), periodEnd, reportType, scope);
        if (local.isPresent()) {
            return reports.findAllLineItems(local.get().getId());
        }
        try {
            ExternalFinancialStatements external = gateway.fetch(instrument, periodEnd, reportType);
            FinancialReport report = toReport(instrument.getId(), external);
            reports.saveReport(report);
            List<FinancialLineItem> lines = toLineItems(report.getId(), external);
            reports.replaceLineItems(report.getId(), external.getSourceCode(), lines);
            return lines;
        } catch (RuntimeException error) {
            dataGaps.add(gapMessage);
            return new ArrayList<FinancialLineItem>();
        }
    }

    private List<FinancialLineItem> deriveSingleQuarter(
            Long reportId, FinancialReportType reportType,
            List<FinancialLineItem> current, List<FinancialLineItem> priorCumulative) {
        Map<String, FinancialLineItem> prior = index(priorCumulative);
        List<FinancialLineItem> derived = new ArrayList<FinancialLineItem>();
        for (FinancialLineItem item : current) {
            if (!"CURRENT_YTD".equals(item.getPeriodRole())
                    || (item.getStatementType() != FinancialStatementType.INCOME
                    && item.getStatementType() != FinancialStatementType.CASH_FLOW)) {
                continue;
            }
            BigDecimal singleQuarter;
            FinancialValueOrigin origin;
            if (reportType == FinancialReportType.Q1) {
                singleQuarter = item.getNormalizedValue();
                origin = FinancialValueOrigin.REPORTED;
            } else {
                FinancialLineItem previous = prior.get(key(item));
                singleQuarter = quarterDerivation.singleQuarter(
                        item.getNormalizedValue(),
                        previous == null ? null : previous.getNormalizedValue());
                origin = FinancialValueOrigin.DERIVED;
            }
            if (singleQuarter == null) {
                continue;
            }
            FinancialLineItem value = new FinancialLineItem();
            value.setReportId(reportId);
            value.setStatementType(item.getStatementType());
            value.setSourceLabel(item.getSourceLabel() + "（单季）");
            value.setConceptCode(item.getConceptCode());
            value.setPeriodRole("CURRENT_QUARTER");
            value.setNormalizedValue(singleQuarter);
            value.setCurrency(item.getCurrency());
            value.setUnitMultiplier(BigDecimal.ONE);
            value.setValueOrigin(origin);
            value.setSourceField(item.getSourceField());
            value.setSourceCode(item.getSourceCode());
            value.setDisplayOrder(item.getDisplayOrder() + 10000);
            value.setQualityStatus(item.getQualityStatus());
            derived.add(value);
        }
        return derived;
    }

    private Map<String, FinancialLineItem> index(List<FinancialLineItem> items) {
        Map<String, FinancialLineItem> result = new HashMap<String, FinancialLineItem>();
        for (FinancialLineItem item : items) {
            if ("CURRENT_YTD".equals(item.getPeriodRole())) {
                result.put(key(item), item);
            }
        }
        return result;
    }

    private String key(FinancialLineItem item) {
        String identity = item.getConceptCode() == null
                ? item.getSourceField() : item.getConceptCode();
        return item.getStatementType().name() + "|" + identity;
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
