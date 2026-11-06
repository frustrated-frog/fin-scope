package com.finscope.service.instrument;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.common.enums.financials.FinancialValueOrigin;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.service.financials.QuarterDerivationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockFinancialQuarterAssemblerTest {
    private static final Long INSTRUMENT_ID = 7L;
    private static final LocalDate Q1 = LocalDate.of(2025, 3, 31);
    private static final LocalDate HALF_YEAR = LocalDate.of(2025, 6, 30);
    private static final LocalDate Q3 = LocalDate.of(2025, 9, 30);
    private static final LocalDate ANNUAL = LocalDate.of(2025, 12, 31);
    private FinancialReportRepository reports;
    private StockFinancialQuarterAssembler assembler;
    private List<FinancialReport> storedReports;
    private long nextLineId;

    @BeforeEach
    void setUp() {
        reports = mock(FinancialReportRepository.class);
        assembler = new StockFinancialQuarterAssembler();
        ReflectionTestUtils.setField(assembler, "reports", reports);
        ReflectionTestUtils.setField(assembler, "quarterDerivation", new QuarterDerivationEngine());
        storedReports = new ArrayList<FinancialReport>();
        nextLineId = 1L;
        when(reports.findReports(INSTRUMENT_ID)).thenAnswer(ignored -> new ArrayList<>(storedReports));
        when(reports.findReport(eq(INSTRUMENT_ID), any(LocalDate.class), any(FinancialReportType.class),
                eq("CONSOLIDATED"))).thenAnswer(invocation -> storedReports.stream()
                .filter(report -> invocation.getArgument(1).equals(report.getPeriodEnd()))
                .filter(report -> invocation.getArgument(2) == report.getReportType())
                .filter(report -> "CONSOLIDATED".equals(report.getScope()))
                .max(Comparator.comparing(FinancialReport::getId)));
    }

    @Test
    void derivesAllFourQuartersInRequestedOrderWithoutApplyingTheUnitMultiplierAgain() {
        fullReport(1L, Q1, "100", "10", "-5", "60");
        fullReport(2L, HALF_YEAR, "240", "7", "15", "150");
        fullReport(3L, Q3, "390", "27", "12", "250");
        FinancialReport annual = fullReport(4L, ANNUAL, "600", "15", "42", "400");
        annual.setPublishedAt(LocalDateTime.of(2026, 3, 25, 10, 0));
        List<StockFinancialQuarter> points = assembler.assemble(INSTRUMENT_ID,
                List.of(ANNUAL, Q1, Q3, HALF_YEAR, Q1));

        assertEquals(List.of(ANNUAL, Q1, Q3, HALF_YEAR, Q1),
                points.stream().map(StockFinancialQuarter::getPeriodEnd).toList());
        assertAmounts(points.get(0), "210", "-12", "30", "28.5714");
        assertAmounts(points.get(1), "100", "10", "-5", "40.0000");
        assertAmounts(points.get(2), "150", "20", "-3", "33.3333");
        assertAmounts(points.get(3), "140", "-3", "20", "35.7143");
        assertEquals(annual.getId(), points.get(0).getReportId());
        assertEquals(annual.getPublishedAt(), points.get(0).getPublishedAt());
        assertEquals(FinancialReportType.ANNUAL, points.get(0).getReportType());
        assertEquals("SINA", points.get(0).getSourceCode());
        assertEquals("CNY", points.get(0).getCurrency());
        assertEquals(FinancialQualityStatus.FRESH, points.get(0).getQualityStatus());
        assertTrue(points.stream().allMatch(point -> point.getDataGaps().isEmpty()));
        verify(reports, times(1)).findReports(INSTRUMENT_ID);
        for (long id = 1L; id <= 4L; id++) {
            verify(reports, times(1)).findAllLineItems(id);
        }
        verify(reports, never()).findMetrics(anyLong());
    }

    @Test
    void missingAdjacentPriorReportNeverUsesCumulativeOrPriorYearAnnualAsQuarter() {
        fullReport(1L, LocalDate.of(2024, 12, 31), "400", "20", "30", "200");
        fullReport(2L, HALF_YEAR, "240", "7", "15", "150");
        StockFinancialQuarter point = assemble(HALF_YEAR);

        assertAllAmountsMissing(point);
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.contains(Q1.toString())
                && gap.contains("前序合并累计财报")));
        verify(reports, never()).findAllLineItems(1L);
    }

    @Test
    void missingCurrentOrPriorMetricSuppressesOnlyItsOwnQuarterAmount() {
        FinancialReport first = report(1L, Q1);
        store(first, line(first, "REVENUE", "100"), line(first, "NET_PROFIT_PARENT", "10"),
                line(first, "OPERATING_COST", "60"));
        FinancialReport half = report(2L, HALF_YEAR);
        store(half, line(half, "REVENUE", "240"), line(half, "OPERATING_CASH_FLOW", "15"),
                line(half, "OPERATING_COST", "150"));
        StockFinancialQuarter point = assemble(HALF_YEAR);

        assertDecimal("140", point.getRevenue());
        assertNull(point.getNetProfitParent());
        assertNull(point.getOperatingCashFlow());
        assertDecimal("35.7143", point.getGrossMarginPct());
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.contains("归母净利润")
                && gap.contains(HALF_YEAR.toString())));
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.contains("经营活动现金流")
                && gap.contains(Q1.toString()) && gap.contains("前序累计值")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE", "CURRENCY", "SCOPE"})
    void rejectsAdjacentReportWithDifferentSourceCurrencyOrScope(String mismatch) {
        FinancialReport prior = fullReport(1L, Q1, "100", "10", "5", "60");
        fullReport(2L, HALF_YEAR, "240", "7", "15", "150");
        if ("SOURCE".equals(mismatch)) {
            prior.setSourceCode("OTHER_PROVIDER");
        } else if ("CURRENCY".equals(mismatch)) {
            prior.setCurrency("USD");
        } else {
            prior.setScope("PARENT");
        }
        StockFinancialQuarter point = assemble(HALF_YEAR);

        assertAllAmountsMissing(point);
        assertFalse(point.getDataGaps().isEmpty());
        assertTrue(point.getDataGaps().get(0).contains(Q1.toString()));
        verify(reports, never()).findAllLineItems(1L);
    }

    @Test
    void acceptsOnlyMatchingSourceCurrencyRoleAndStatementAndNeverUsesTotalOperatingCost() {
        FinancialReport current = report(1L, Q1);
        FinancialLineItem wrongSource = line(current, "REVENUE", "100");
        wrongSource.setSourceCode("OTHER_PROVIDER");
        FinancialLineItem wrongCurrency = line(current, "NET_PROFIT_PARENT", "10");
        wrongCurrency.setCurrency("USD");
        FinancialLineItem wrongStatement = line(current, "OPERATING_CASH_FLOW", "5");
        wrongStatement.setStatementType(FinancialStatementType.INCOME);
        FinancialLineItem alreadyDerived = line(current, "REVENUE", "50");
        alreadyDerived.setPeriodRole("CURRENT_QUARTER");
        FinancialLineItem wrongIncomeStatement = line(current, "OPERATING_COST", "60");
        wrongIncomeStatement.setStatementType(FinancialStatementType.CASH_FLOW);
        FinancialLineItem wrongReport = line(current, "REVENUE", "200");
        wrongReport.setReportId(999L);
        store(current, wrongSource, wrongCurrency, wrongStatement, alreadyDerived,
                wrongIncomeStatement, wrongReport, line(current, "TOTAL_OPERATING_COST", "70"));
        StockFinancialQuarter point = assemble(Q1);

        assertAllAmountsMissing(point);
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.startsWith("营业成本：")));
        assertFalse(assembler.hasUsableReport(INSTRUMENT_ID, Q1));
    }

    @Test
    void totalOperatingCostAloneCannotProduceGrossMarginEvenWhenRevenueIsAvailable() {
        FinancialReport current = report(1L, Q1);
        store(current, line(current, "REVENUE", "100"), line(current, "TOTAL_OPERATING_COST", "70"));
        StockFinancialQuarter point = assemble(Q1);

        assertDecimal("100", point.getRevenue());
        assertNull(point.getGrossMarginPct());
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.startsWith("营业成本：")));
    }

    @Test
    void derivesOperatingCostAliasAcrossAdjacentReportsAndKeepsPartialProviderWarning() {
        FinancialReport prior = report(1L, Q1);
        store(prior, line(prior, "REVENUE", "100"), line(prior, "COST_OF_REVENUE", "60"));
        FinancialReport current = report(2L, HALF_YEAR);
        current.setQualityStatus(FinancialQualityStatus.PARTIAL);
        current.setWarningMessage("上游缺少现金流量表");
        store(current, line(current, "REVENUE", "240"), line(current, "OPERATING_COST", "150"),
                line(current, "TOTAL_OPERATING_COST", "230"));
        StockFinancialQuarter point = assemble(HALF_YEAR);

        assertDecimal("35.7143", point.getGrossMarginPct());
        assertEquals(FinancialQualityStatus.PARTIAL, point.getQualityStatus());
        assertTrue(point.getDataGaps().contains("上游缺少现金流量表"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nonpositiveQuarterRevenueCannotProduceGrossMargin(String revenue) {
        fullReport(1L, Q1, revenue, "10", "5", "60");
        StockFinancialQuarter point = assemble(Q1);

        assertDecimal(revenue, point.getRevenue());
        assertNull(point.getGrossMarginPct());
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.contains("营业收入不大于0")));
    }

    @ParameterizedTest
    @EnumSource(value = FinancialQualityStatus.class, names = {"CONFLICT", "UNAVAILABLE"})
    void rejectsConflictedOrUnavailableLinesAndReports(FinancialQualityStatus quality) {
        FinancialReport current = report(1L, Q1);
        FinancialLineItem revenue = line(current, "REVENUE", "100");
        revenue.setQualityStatus(quality);
        store(current, revenue);
        assertNull(assemble(Q1).getRevenue());
        assertFalse(assembler.hasUsableReport(INSTRUMENT_ID, Q1));

        revenue.setQualityStatus(FinancialQualityStatus.FRESH);
        current.setQualityStatus(quality);
        StockFinancialQuarter point = assemble(Q1);
        assertAllAmountsMissing(point);
        assertEquals(quality, point.getQualityStatus());
        assertTrue(point.getDataGaps().stream().anyMatch(gap -> gap.contains(quality.name())));
        assertFalse(assembler.hasUsableReport(INSTRUMENT_ID, Q1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE_NULL", "SOURCE_BLANK", "CURRENCY_NULL", "CURRENCY_BLANK"})
    void rejectsMissingReportContextEvenIfLinesShareItsNullOrBlankValue(String missing) {
        FinancialReport current = report(1L, Q1);
        if (missing.startsWith("SOURCE")) {
            current.setSourceCode(missing.endsWith("NULL") ? null : " ");
        } else {
            current.setCurrency(missing.endsWith("NULL") ? null : " ");
        }
        store(current, line(current, "REVENUE", "100"));

        assertAllAmountsMissing(assemble(Q1));
        assertFalse(assembler.hasUsableReport(INSTRUMENT_ID, Q1));
        verify(reports, never()).findAllLineItems(1L);
    }

    @Test
    void choosesLatestMatchingConsolidatedReportWithoutFallingBackWhenItsQualityIsInvalid() {
        fullReport(1L, Q1, "100", "10", "5", "60");
        FinancialReport latest = fullReport(2L, Q1, "110", "11", "6", "60");
        FinancialReport parent = fullReport(3L, Q1, "999", "99", "99", "99");
        parent.setScope("PARENT");
        FinancialReport incorrectType = fullReport(4L, Q1, "888", "88", "88", "88");
        incorrectType.setReportType(FinancialReportType.ANNUAL);
        FinancialReport foreignInstrument = fullReport(5L, Q1, "777", "77", "77", "77");
        foreignInstrument.setInstrumentId(8L);
        assertDecimal("110", assemble(Q1).getRevenue());

        latest.setQualityStatus(FinancialQualityStatus.CONFLICT);
        StockFinancialQuarter point = assemble(Q1);
        assertAllAmountsMissing(point);
        assertEquals(2L, point.getReportId());
        verify(reports, never()).findAllLineItems(1L);
        verify(reports, never()).findAllLineItems(3L);
        verify(reports, never()).findAllLineItems(4L);
        verify(reports, never()).findAllLineItems(5L);
    }

    @Test
    void preservesExpectedPeriodTypeAndPreciseGapWhenCurrentReportIsMissing() {
        StockFinancialQuarter point = assemble(Q3);

        assertEquals(Q3, point.getPeriodEnd());
        assertEquals(FinancialReportType.Q3, point.getReportType());
        assertEquals(FinancialQualityStatus.UNAVAILABLE, point.getQualityStatus());
        assertNull(point.getReportId());
        assertAllAmountsMissing(point);
        assertTrue(point.getDataGaps().get(0).contains(Q3.toString()));
        verify(reports, never()).findAllLineItems(anyLong());
    }

    @Test
    void treatsOneUsableRawCumulativeLineAsLocalDataWithoutRequiringAnalysisOrCurrentQuarterRows() {
        FinancialReport current = report(1L, HALF_YEAR);
        current.setQualityStatus(FinancialQualityStatus.PARTIAL);
        store(current, line(current, "REVENUE", "0"));

        assertTrue(assembler.hasUsableReport(INSTRUMENT_ID, HALF_YEAR));
        assertFalse(assembler.hasUsableReport(INSTRUMENT_ID, Q1));
        verify(reports, never()).findReports(anyLong());
        verify(reports, never()).findMetrics(anyLong());
    }

    @Test
    void ignoresNullRawAmountInsteadOfTreatingItAsZero() {
        FinancialReport current = report(1L, Q1);
        store(current, line(current, "REVENUE", null));

        assertNull(assemble(Q1).getRevenue());
        assertFalse(assembler.hasUsableReport(INSTRUMENT_ID, Q1));
    }

    @Test
    void validatesQuarterDatesBeforeReadingReportsAndSkipsReadsForEmptyPeriods() {
        assertThrows(BusinessException.class, () -> assembler.assemble(null, List.of(Q1)));
        assertThrows(BusinessException.class, () -> assembler.assemble(INSTRUMENT_ID,
                List.of(LocalDate.of(2025, 3, 30))));
        assertThrows(BusinessException.class, () -> assembler.hasUsableReport(INSTRUMENT_ID,
                LocalDate.of(2025, 4, 30)));
        assertTrue(assembler.assemble(INSTRUMENT_ID, List.of()).isEmpty());
        verify(reports, never()).findReports(anyLong());
    }

    private StockFinancialQuarter assemble(LocalDate period) {
        return assembler.assemble(INSTRUMENT_ID, List.of(period)).get(0);
    }

    private FinancialReport fullReport(Long id, LocalDate period, String revenue, String profit,
                                        String cashFlow, String cost) {
        FinancialReport report = report(id, period);
        store(report, line(report, "REVENUE", revenue), line(report, "NET_PROFIT_PARENT", profit),
                line(report, "OPERATING_CASH_FLOW", cashFlow), line(report, "OPERATING_COST", cost));
        return report;
    }

    private FinancialReport report(Long id, LocalDate period) {
        FinancialReport report = new FinancialReport();
        report.setId(id);
        report.setInstrumentId(INSTRUMENT_ID);
        report.setPeriodEnd(period);
        report.setReportType(switch (period.getMonthValue()) {
            case 3 -> FinancialReportType.Q1;
            case 6 -> FinancialReportType.HALF_YEAR;
            case 9 -> FinancialReportType.Q3;
            default -> FinancialReportType.ANNUAL;
        });
        report.setScope("CONSOLIDATED");
        report.setSourceCode("SINA");
        report.setCurrency("CNY");
        report.setQualityStatus(FinancialQualityStatus.FRESH);
        return report;
    }

    private FinancialLineItem line(FinancialReport report, String concept, String amount) {
        FinancialLineItem line = new FinancialLineItem();
        line.setId(nextLineId++);
        line.setReportId(report.getId());
        line.setConceptCode(concept);
        line.setStatementType("OPERATING_CASH_FLOW".equals(concept)
                ? FinancialStatementType.CASH_FLOW : FinancialStatementType.INCOME);
        line.setPeriodRole("CURRENT_YTD");
        line.setNormalizedValue(amount == null ? null : new BigDecimal(amount));
        line.setUnitMultiplier(new BigDecimal("10000"));
        line.setCurrency(report.getCurrency());
        line.setSourceCode(report.getSourceCode());
        line.setQualityStatus(FinancialQualityStatus.FRESH);
        line.setValueOrigin(FinancialValueOrigin.REPORTED);
        return line;
    }

    private void store(FinancialReport report, FinancialLineItem... lines) {
        storedReports.add(report);
        when(reports.findAllLineItems(report.getId())).thenReturn(List.of(lines));
    }

    private static void assertAmounts(StockFinancialQuarter point, String revenue, String profit,
                                       String cashFlow, String grossMargin) {
        assertDecimal(revenue, point.getRevenue());
        assertDecimal(profit, point.getNetProfitParent());
        assertDecimal(cashFlow, point.getOperatingCashFlow());
        assertDecimal(grossMargin, point.getGrossMarginPct());
    }

    private static void assertAllAmountsMissing(StockFinancialQuarter point) {
        assertNull(point.getRevenue());
        assertNull(point.getNetProfitParent());
        assertNull(point.getOperatingCashFlow());
        assertNull(point.getGrossMarginPct());
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
