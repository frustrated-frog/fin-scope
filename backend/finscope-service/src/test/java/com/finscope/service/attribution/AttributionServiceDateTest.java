package com.finscope.service.attribution;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.attribution.AttributionMarketContext;
import com.finscope.domain.attribution.AttributionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttributionServiceDateTest {
    private AttributionService service;
    private AttributionRepository reports;
    private AttributionMarketContextService market;
    private AttributionHarness harness;
    private Executor executor;
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setup() {
        service = new AttributionService();
        reports = mock(AttributionRepository.class);
        market = mock(AttributionMarketContextService.class);
        harness = mock(AttributionHarness.class);
        executor = mock(Executor.class);
        ReflectionTestUtils.setField(service, "instrumentRepository", mock(InstrumentRepository.class));
        ReflectionTestUtils.setField(service, "attributionRepository", reports);
        ReflectionTestUtils.setField(service, "marketContextService", market);
        ReflectionTestUtils.setField(service, "attributionHarness", harness);
        ReflectionTestUtils.setField(service, "progressPublisher", mock(AttributionProgressPublisher.class));
        ReflectionTestUtils.setField(service, "executor", executor);
        when(reports.createReport(any())).thenAnswer(call -> {
            AttributionReport report = call.getArgument(0);
            report.setId(88L);
            return report;
        });
    }

    @Test
    void historicalDateAndActualReturnReachTheResearchInsteadOfCurrentQuote() {
        AttributionMarketContext context = new AttributionMarketContext();
        context.setQuoteVerified(true);
        context.setStockChangePct(-6.8);
        when(market.capture(any(), eq(today.minusDays(1)))).thenReturn(context);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());

        service.startAttribution("600519", "STOCK", "贵州茅台", 9.99, today.minusDays(1).toString());

        verify(reports).createReport(argThat(report -> today.minusDays(1).equals(report.getReportDate())
                && Double.valueOf(-6.8).equals(report.getChangePct())));
        verify(harness).research(argThat(report -> today.minusDays(1).equals(report.getReportDate())), any(), eq(-6.8), anyString(), any());
        verify(reports).updateResult(argThat(report -> "COMPLETED".equals(report.getStatus())));
    }

    @Test
    void acceptsOldestSelectableDateAndTodayDefault() {
        AttributionMarketContext context = new AttributionMarketContext();
        context.setQuoteVerified(true);
        context.setStockChangePct(2.0);
        when(market.capture(any(), eq(today.minusDays(2)))).thenReturn(context);
        service.startAttribution("600519", "STOCK", "贵州茅台", null, today.minusDays(2).toString());
        service.startAttribution("600519", "STOCK", "贵州茅台", 1.0, null);
        verify(reports).createReport(argThat(report -> today.minusDays(2).equals(report.getReportDate())));
        verify(reports).createReport(argThat(report -> today.equals(report.getReportDate())));
    }

    @Test
    void rejectsInvalidFutureAndExpiredDatesWithoutCreatingTasks() {
        for (String date : new String[]{"not-a-date", "2026-02-30", today.plusDays(1).toString(), today.minusDays(3).toString()}) {
            assertThrows(BusinessException.class,
                    () -> service.startAttribution("600519", "STOCK", "贵州茅台", 1.0, date));
        }
        verifyNoInteractions(reports, executor, market);
    }

    @Test
    void missingHistoricalSessionDoesNotSilentlyUseAnotherDayOrCreateReport() {
        when(market.capture(any(), any())).thenReturn(new AttributionMarketContext());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startAttribution("600519", "STOCK", "贵州茅台", 9.99, today.minusDays(1).toString()));
        assertTrue(error.getMessage().contains("暂无可用日线行情"));
        verifyNoInteractions(reports, executor);
    }

    @Test
    void fundQuoteDatesRemainCompatibleWithoutStockHistoryLookup() {
        LocalDate navDate = today.minusDays(5);
        service.startAttribution("021894", "FUND", "基金", 1.2, navDate.toString());
        verify(reports).createReport(argThat(report -> navDate.equals(report.getReportDate())
                && Double.valueOf(1.2).equals(report.getChangePct())));
        verifyNoInteractions(market);
    }
}
