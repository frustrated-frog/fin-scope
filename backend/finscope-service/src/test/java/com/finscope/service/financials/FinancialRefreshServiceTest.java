package com.finscope.service.financials;

import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.financials.ExternalFinancialStatements;
import com.finscope.rpc.financials.StructuredFinancialDataGateway;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialRefreshServiceTest {
    @Test
    void refreshAppliesATotalProviderDeadline() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        Instrument stock = stock();
        when(instruments.findById(7L)).thenReturn(Optional.of(stock));
        AtomicLong remainingMillis = new AtomicLong(Long.MAX_VALUE);
        StructuredFinancialDataGateway gateway = new StructuredFinancialDataGateway() {
            public boolean supports(Instrument instrument) { return true; }
            public String providerCode() { return "DEADLINE_TEST"; }
            public ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                                     FinancialReportType reportType) {
                remainingMillis.set(ProviderCallDeadline.remainingMillis());
                throw new IllegalStateException("stop after observing deadline");
            }
        };
        FinancialRefreshService service = new FinancialRefreshService(
                instruments, mock(FinancialReportRepository.class), gateway,
                new FinancialAnalysisEngine(), new QuarterDerivationEngine());

        assertThrows(IllegalStateException.class, () -> service.refresh(
                7L, LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL));

        assertTrue(remainingMillis.get() > 0L);
        assertTrue(remainingMillis.get() <= 60_000L);
        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
    }

    @Test
    void refreshDoesNotHoldADatabaseTransactionAcrossRemoteFetches() throws Exception {
        assertNull(FinancialRefreshService.class
                .getMethod("refresh", Long.class, LocalDate.class, FinancialReportType.class)
                .getAnnotation(Transactional.class));
        assertNull(GlobalFinancialRefreshService.class
                .getMethod("refresh", String.class, String.class, String.class, String.class,
                        String.class, LocalDate.class, FinancialReportType.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void refreshPersistsThreeStatementsAndReturnsAnalyzedView() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialReportRepository reports = mock(FinancialReportRepository.class);
        StructuredFinancialDataGateway gateway = mock(StructuredFinancialDataGateway.class);
        Instrument stock = stock();
        when(instruments.findById(7L)).thenReturn(Optional.of(stock));
        when(gateway.fetch(eq(stock), eq(LocalDate.of(2026, 6, 30)),
                eq(FinancialReportType.HALF_YEAR))).thenReturn(external());
        when(gateway.fetch(eq(stock), eq(LocalDate.of(2026, 3, 31)),
                eq(FinancialReportType.Q1))).thenReturn(reference(
                        LocalDate.of(2026, 3, 31), FinancialReportType.Q1, "500000000.00"));
        when(gateway.fetch(eq(stock), eq(LocalDate.of(2025, 6, 30)),
                eq(FinancialReportType.HALF_YEAR))).thenReturn(reference(
                        LocalDate.of(2025, 6, 30), FinancialReportType.HALF_YEAR, "1000000000.00"));
        when(reports.saveReport(any(FinancialReport.class))).thenAnswer(invocation -> {
            FinancialReport value = invocation.getArgument(0);
            if (value.getPeriodEnd().equals(LocalDate.of(2026, 6, 30))) {
                value.setId(9L);
            } else if (value.getPeriodEnd().equals(LocalDate.of(2026, 3, 31))) {
                value.setId(8L);
            } else {
                value.setId(7L);
            }
            return value;
        });
        when(reports.findReport(eq(7L), any(LocalDate.class), any(FinancialReportType.class),
                eq("CONSOLIDATED"))).thenReturn(Optional.empty());

        FinancialRefreshService service = new FinancialRefreshService(
                instruments, reports, gateway, new FinancialAnalysisEngine(),
                new QuarterDerivationEngine());

        FinancialReportView view = service.refresh(
                7L, LocalDate.of(2026, 6, 30), FinancialReportType.HALF_YEAR);

        assertEquals(9L, view.getReport().getId());
        assertEquals(3, view.getStatements().size());
        assertEquals(new BigDecimal("1200000000.12"),
                view.getStatements().get(FinancialStatementType.INCOME).get(0).getNormalizedValue());
        assertEquals(new BigDecimal("700000000.12"),
                view.getStatements().get(FinancialStatementType.INCOME).stream()
                        .filter(item -> "CURRENT_QUARTER".equals(item.getPeriodRole()))
                        .findFirst().get().getNormalizedValue());
        assertEquals("DERIVED",
                view.getStatements().get(FinancialStatementType.INCOME).stream()
                        .filter(item -> "CURRENT_QUARTER".equals(item.getPeriodRole()))
                        .findFirst().get().getValueOrigin().name());
        verify(reports).replaceLineItems(eq(9L), eq("AKSHARE"), any());
        verify(reports).replaceAnalysis(eq(9L), any(), any());
        verify(gateway, atLeastOnce()).fetch(stock, LocalDate.of(2026, 3, 31),
                FinancialReportType.Q1);
        verify(gateway, atLeastOnce()).fetch(stock, LocalDate.of(2025, 6, 30),
                FinancialReportType.HALF_YEAR);
    }

    private Instrument stock() {
        Instrument value = new Instrument();
        value.setId(7L);
        value.setCode("600519");
        value.setName("贵州茅台");
        value.setType("STOCK");
        value.setMarket("SH");
        return value;
    }

    private ExternalFinancialStatements external() {
        ExternalFinancialStatements result = new ExternalFinancialStatements();
        result.setPeriodEnd(LocalDate.of(2026, 6, 30));
        result.setReportType(FinancialReportType.HALF_YEAR);
        result.setScope("CONSOLIDATED");
        result.setCurrency("CNY");
        result.setAudited(false);
        result.setSourceCode("AKSHARE");
        result.setQualityStatus(FinancialQualityStatus.FRESH);
        result.setStatements(Arrays.asList(
                statement(FinancialStatementType.INCOME, "营业收入", "REVENUE",
                        "1200000000.12", "CURRENT_YTD"),
                statement(FinancialStatementType.BALANCE_SHEET, "资产总计", "TOTAL_ASSETS",
                        "3400000000", "CURRENT_PERIOD_END"),
                statement(FinancialStatementType.CASH_FLOW, "经营活动现金流量净额",
                        "OPERATING_CASH_FLOW", "180000000", "CURRENT_YTD")
        ));
        return result;
    }

    private ExternalFinancialStatements reference(LocalDate periodEnd,
                                                   FinancialReportType reportType,
                                                   String revenue) {
        ExternalFinancialStatements result = new ExternalFinancialStatements();
        result.setPeriodEnd(periodEnd);
        result.setReportType(reportType);
        result.setScope("CONSOLIDATED");
        result.setCurrency("CNY");
        result.setSourceCode("AKSHARE");
        result.setQualityStatus(FinancialQualityStatus.FRESH);
        result.setStatements(Arrays.asList(
                statement(FinancialStatementType.INCOME, "营业收入", "REVENUE",
                        revenue, "CURRENT_YTD"),
                statement(FinancialStatementType.BALANCE_SHEET, "资产总计", "TOTAL_ASSETS",
                        "3000000000", "CURRENT_PERIOD_END"),
                statement(FinancialStatementType.CASH_FLOW, "经营活动现金流量净额",
                        "OPERATING_CASH_FLOW", "100000000", "CURRENT_YTD")
        ));
        return result;
    }

    private ExternalFinancialStatements.Statement statement(
            FinancialStatementType type, String label, String code, String value, String periodRole) {
        ExternalFinancialStatements.Value line = new ExternalFinancialStatements.Value();
        line.setSourceLabel(label);
        line.setConceptCode(code);
        line.setValue(new BigDecimal(value));
        line.setUnitMultiplier(BigDecimal.ONE);
        line.setPeriodRole(periodRole);
        ExternalFinancialStatements.Statement statement = new ExternalFinancialStatements.Statement();
        statement.setStatementType(type);
        statement.setValues(Collections.singletonList(line));
        return statement;
    }
}
