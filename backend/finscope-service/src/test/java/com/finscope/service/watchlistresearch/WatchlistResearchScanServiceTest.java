package com.finscope.service.watchlistresearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.watchlistresearch.ResearchScanRepository;
import com.finscope.dao.watchlistresearch.ResearchScanSchemaMigrator;
import com.finscope.domain.instrument.*;
import com.finscope.domain.watchlistresearch.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WatchlistResearchScanServiceTest {
    @TempDir
    Path directory;
    private WatchlistResearchScanService service;
    private WatchlistRepository watchlist;
    private WatchlistResearchInputs inputs;
    private ResearchScanRepository repository;
    private final List<Runnable> jobs = new ArrayList<Runnable>();

    @BeforeEach
    void setUp() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("scans.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        ResearchScanSchemaMigrator migrator = new ResearchScanSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbc", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager", new DataSourceTransactionManager(source));
        migrator.afterPropertiesSet();
        repository = new ResearchScanRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        ReflectionTestUtils.setField(repository, "json", new ObjectMapper().registerModule(new JavaTimeModule()));
        watchlist = mock(WatchlistRepository.class);
        inputs = mock(WatchlistResearchInputs.class);
        when(watchlist.findByTypes(List.of("STOCK"))).thenReturn(List.of(stock("600001"), stock("600002")));
        when(inputs.market(anyString(), anyString(), anyBoolean())).thenAnswer(call -> prices(call.getArgument(0)));
        when(inputs.financials(anyString(), anyBoolean())).thenReturn(financials());
        service = new WatchlistResearchScanService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "watchlist", watchlist);
        ReflectionTestUtils.setField(service, "inputs", inputs);
        ReflectionTestUtils.setField(service, "executor", (Executor) jobs::add);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-11-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void repeatedVisitsAndSchedulerShareOneAutomaticJobAndPersistFrozenResults() {
        ResearchScanView running = service.view(false);
        service.view(false);
        service.dailyScan();
        assertEquals(1, jobs.size());
        assertEquals(LocalDate.of(2026, 11, 6), running.getActive().getScanDate());
        jobs.remove(0).run();
        ResearchScanView ready = service.view(false);
        assertNull(ready.getActive());
        assertTrue(jobs.isEmpty());
        assertEquals(ResearchScanStatus.READY, ready.getLatest().getStatus());
        assertEquals(2, ready.getLatest().getSnapshot().getMarketCovered());
        assertEquals(2, ready.getLatest().getSnapshot().getFinancialCovered());
        assertEquals(1, ready.getHistory().size());
        assertNotNull(service.history(ready.getLatest().getId()).getSnapshot());
        verify(inputs, times(1)).market("000300", "SH", false);
    }

    @Test
    void oneStockFailurePreservesOtherStockAndAllFinancialResults() {
        when(inputs.market("600001", "SH", false)).thenThrow(new IllegalStateException("offline"));
        service.view(false);
        jobs.remove(0).run();
        WatchlistResearchSnapshot snapshot = service.view(false).getLatest().getSnapshot();
        assertEquals(1, snapshot.getMarketCovered());
        assertEquals(2, snapshot.getFinancialCovered());
        assertEquals(1, snapshot.getStrengths().size());
        assertEquals(ResearchScanStatus.PARTIAL, service.view(false).getLatest().getStatus());
        assertTrue(snapshot.getGaps().stream().anyMatch(gap -> gap.contains("600001")));
    }

    @Test
    void benchmarkFailureDoesNotSuppressIndependentFinancialResearch() {
        when(inputs.market("000300", "SH", false)).thenThrow(new IllegalStateException("offline"));
        service.view(false);
        jobs.remove(0).run();
        WatchlistResearchSnapshot snapshot = service.view(false).getLatest().getSnapshot();
        assertEquals(0, snapshot.getMarketCovered());
        assertEquals(2, snapshot.getFinancialCovered());
    }

    @Test
    void refreshKeepsPreviousCompletedResultsAndArchivesNewRun() {
        service.view(false);
        jobs.remove(0).run();
        String first = service.view(false).getLatest().getId();
        ResearchScanView refreshing = service.view(true);
        assertEquals(first, refreshing.getLatest().getId());
        assertNotEquals(first, refreshing.getActive().getId());
        jobs.remove(0).run();
        assertEquals(2, service.view(false).getHistory().size());
        assertNotNull(service.history(first).getSnapshot());
        verify(inputs).financials("600001", true);
        verify(inputs).financials("600002", true);
    }

    @Test
    void totalInputFailureDoesNotReplaceTheLastCompletedSnapshot() {
        service.view(false);
        jobs.remove(0).run();
        String previous = service.view(false).getLatest().getId();
        when(inputs.market(anyString(), anyString(), eq(true))).thenThrow(new IllegalStateException("offline"));
        when(inputs.financials(anyString(), eq(true))).thenThrow(new IllegalStateException("offline"));
        service.view(true);
        jobs.remove(0).run();
        ResearchScanView view = service.view(false);
        assertEquals(previous, view.getLatest().getId());
        assertEquals(ResearchScanStatus.FAILED, view.getLastAttempt().getStatus());
        assertEquals(1, view.getHistory().size());
    }

    @Test
    void membershipChangeSchedulesNewInputSetAndDoesNotReuseRemovedStocksSnapshot() {
        service.view(false);
        jobs.remove(0).run();
        when(watchlist.findByTypes(List.of("STOCK"))).thenReturn(List.of(stock("600002")));
        ResearchScanView changed = service.view(false);
        assertNull(changed.getLatest());
        assertEquals(1, changed.getActive().getTotalStocks());
        jobs.remove(0).run();
        assertEquals(1, service.view(false).getLatest().getSnapshot().getTotalStocks());
    }

    @Test
    void emptyWatchlistDoesNotFetchAndRejectedExecutorReleasesActiveSlot() {
        when(watchlist.findByTypes(List.of("STOCK"))).thenReturn(List.of());
        assertNull(service.view(false).getActive());
        verifyNoInteractions(inputs);
        when(watchlist.findByTypes(List.of("STOCK"))).thenReturn(List.of(stock("600001")));
        ReflectionTestUtils.setField(service, "executor", (Executor) job -> {
            throw new RejectedExecutionException("busy");
        });
        assertNull(service.view(false).getActive());
        assertEquals(ResearchScanStatus.FAILED, service.view(false).getLastAttempt().getStatus());
        assertTrue(jobs.isEmpty());
    }

    private WatchlistItem stock(String code) {
        WatchlistItem result = new WatchlistItem();
        result.setCode(code);
        result.setInstrumentId(Long.valueOf(code));
        result.setMarket("SH");
        result.setType("STOCK");
        result.setName(code);
        return result;
    }

    private DailyBarHistory prices(String code) {
        DailyBarHistory result = new DailyBarHistory();
        result.setQualityStatus("FRESH_PRIMARY");
        double price = 100;
        for (int index = 0; index < 160; index++) {
            price *= 1 + Math.sin(index) * .01 + (code.equals("000300") ? 0 : Math.cos(index * .23) * .002);
            DailyBarPoint point = new DailyBarPoint();
            point.setTradeDate(LocalDate.of(2026, 11, 6).minusDays(159 - index));
            point.setClose(BigDecimal.valueOf(price));
            point.setAdjustment("QFQ");
            point.setIsComplete(true);
            result.getBars().add(point);
        }
        return result;
    }

    private StockFundamentals financials() {
        StockFundamentals result = new StockFundamentals();
        for (int index = 0; index < 8; index++) {
            StockFinancialQuarter point = new StockFinancialQuarter();
            point.setPeriodEnd(YearMonth.of(2024, 12).plusMonths(index * 3L).atEndOfMonth());
            point.setCurrency("CNY");
            point.setSourceCode("REPORTS");
            point.setQualityStatus(FinancialQualityStatus.FRESH);
            point.setRevenue(new BigDecimal("100"));
            point.setNetProfitParent(new BigDecimal("10"));
            point.setOperatingCashFlow(new BigDecimal("12"));
            result.getQuarters().add(point);
        }
        return result;
    }
}
