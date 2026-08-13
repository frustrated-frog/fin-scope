package com.finscope.service.financials;

import com.finscope.domain.financials.BrokerResearchClaim;
import com.finscope.domain.financials.BrokerResearchForecast;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.common.enums.financials.FinancialStatementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerResearchFinancialLinkerTest {
    private final BrokerResearchFinancialLinker linker = new BrokerResearchFinancialLinker();

    @Test
    void comparesMatureForecastWithAnnualFinancialFactAndLinksClaimEvidence() {
        BrokerResearchForecast revenue = forecast("REVENUE", "1000", "CNY", LocalDate.of(2025, 12, 31));
        BrokerResearchClaim marginClaim = new BrokerResearchClaim();
        marginClaim.setFinancialMetricCode("GROSS_MARGIN");
        marginClaim.setTitle("毛利率改善");

        linker.link(view(), Arrays.asList(revenue), Arrays.asList(marginClaim));

        assertEquals(new BigDecimal("1100"), revenue.getActualValue());
        assertEquals(new BigDecimal("10.00"), revenue.getVariancePercent());
        assertEquals("VERIFIED", revenue.getVerificationStatus());
        assertEquals("EVIDENCE_FOUND", marginClaim.getVerificationStatus());
        assertEquals("毛利率", marginClaim.getEvidenceLabel());
        assertEquals("55", marginClaim.getEvidenceValue());
    }

    @Test
    void keepsFutureForecastPendingAndMissingMetricEvidenceInsufficient() {
        BrokerResearchForecast future = forecast("NET_PROFIT_PARENT", "500", "CNY",
                LocalDate.of(2026, 12, 31));
        BrokerResearchClaim unsupported = new BrokerResearchClaim();
        unsupported.setFinancialMetricCode("ROIC");

        linker.link(view(), Arrays.asList(future), Arrays.asList(unsupported));

        assertEquals("PENDING", future.getVerificationStatus());
        assertEquals("INSUFFICIENT_EVIDENCE", unsupported.getVerificationStatus());
    }

    @Test
    void refusesToCompareForecastAgainstADifferentFiscalYear() {
        BrokerResearchForecast priorYear = forecast("REVENUE", "1000", "CNY",
                LocalDate.of(2024, 12, 31));

        linker.link(view(), Arrays.asList(priorYear), java.util.Collections.emptyList());

        assertEquals("INSUFFICIENT_EVIDENCE", priorYear.getVerificationStatus());
        assertEquals(null, priorYear.getActualValue());
    }

    @Test
    void normalizesChineseCurrencyUnitsBeforeComparing() {
        BrokerResearchForecast revenue = forecast("REVENUE", "0.00001", "亿元",
                LocalDate.of(2025, 12, 31));

        linker.link(view(), Arrays.asList(revenue), java.util.Collections.emptyList());

        assertEquals(new BigDecimal("1100"), revenue.getActualValue());
        assertEquals(new BigDecimal("10.00"), revenue.getVariancePercent());
        assertEquals("VERIFIED", revenue.getVerificationStatus());
    }

    @Test
    void reportsGrossMarginDifferenceInPercentagePoints() {
        BrokerResearchForecast margin = forecast("GROSS_MARGIN", "50", "%",
                LocalDate.of(2025, 12, 31));

        linker.link(view(), Arrays.asList(margin), java.util.Collections.emptyList());

        assertEquals(new BigDecimal("5.00"), margin.getVariancePercent());
        assertEquals("VERIFIED", margin.getVerificationStatus());
    }

    private BrokerResearchForecast forecast(String code, String value, String unit, LocalDate period) {
        BrokerResearchForecast forecast = new BrokerResearchForecast();
        forecast.setMetricCode(code);
        forecast.setMetricLabel(code);
        forecast.setForecastValue(new BigDecimal(value));
        forecast.setUnit(unit);
        forecast.setForecastPeriod(period);
        return forecast;
    }

    private FinancialReportView view() {
        FinancialReport report = new FinancialReport();
        report.setId(9L);
        report.setPeriodEnd(LocalDate.of(2025, 12, 31));
        report.setReportType(FinancialReportType.ANNUAL);
        FinancialLineItem revenue = new FinancialLineItem();
        revenue.setConceptCode("REVENUE");
        revenue.setSourceLabel("营业收入");
        revenue.setNormalizedValue(new BigDecimal("1100"));
        revenue.setCurrency("CNY");
        revenue.setStatementType(FinancialStatementType.INCOME);
        FinancialMetric margin = new FinancialMetric();
        margin.setMetricCode("GROSS_MARGIN");
        margin.setLabel("毛利率");
        margin.setValue(new BigDecimal("55"));
        margin.setUnit("%");
        margin.setQualityStatus(FinancialQualityStatus.FRESH);
        FinancialReportView view = new FinancialReportView();
        view.setReport(report);
        view.setMetrics(Arrays.asList(margin));
        EnumMap<FinancialStatementType, java.util.List<FinancialLineItem>> statements =
                new EnumMap<FinancialStatementType, java.util.List<FinancialLineItem>>(FinancialStatementType.class);
        for (FinancialStatementType type : FinancialStatementType.values()) statements.put(type, new ArrayList<FinancialLineItem>());
        statements.get(FinancialStatementType.INCOME).add(revenue);
        view.setStatements(statements);
        return view;
    }
}
