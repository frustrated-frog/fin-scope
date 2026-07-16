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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialRefreshServiceTest {
    @Test
    void refreshPersistsThreeStatementsAndReturnsAnalyzedView() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialReportRepository reports = mock(FinancialReportRepository.class);
        StructuredFinancialDataGateway gateway = mock(StructuredFinancialDataGateway.class);
        Instrument stock = stock();
        when(instruments.findById(7L)).thenReturn(Optional.of(stock));
        when(gateway.fetch(eq(stock), eq(LocalDate.of(2026, 6, 30)),
                eq(FinancialReportType.HALF_YEAR))).thenReturn(external());
        when(reports.saveReport(any(FinancialReport.class))).thenAnswer(invocation -> {
            FinancialReport value = invocation.getArgument(0);
            value.setId(9L);
            return value;
        });
        when(reports.findReports(7L)).thenReturn(Collections.emptyList());

        FinancialRefreshService service = new FinancialRefreshService(
                instruments, reports, gateway, new FinancialAnalysisEngine());

        FinancialReportView view = service.refresh(
                7L, LocalDate.of(2026, 6, 30), FinancialReportType.HALF_YEAR);

        assertEquals(9L, view.getReport().getId());
        assertEquals(3, view.getStatements().size());
        assertEquals(new BigDecimal("1200000000.12"),
                view.getStatements().get(FinancialStatementType.INCOME).get(0).getNormalizedValue());
        verify(reports).replaceLineItems(eq(9L), eq("AKSHARE"), any());
        verify(reports).replaceAnalysis(eq(9L), any(), any());
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
