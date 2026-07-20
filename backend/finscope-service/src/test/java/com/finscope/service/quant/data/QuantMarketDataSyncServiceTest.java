package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.quant.QuantDataSyncRunRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataSyncRun;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantMarketDataSyncServiceTest {

    @Test
    void keepsSuccessfulInstrumentWhenAnotherInstrumentFails() {
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantMarketDataRepository marketData = mock(QuantMarketDataRepository.class);
        QuantDataSyncRunRepository runs = mock(QuantDataSyncRunRepository.class);
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        QuantDataset dataset = eligibleDataset();
        when(datasets.get(7L)).thenReturn(dataset);
        when(marketData.findUniverseMembers(7L)).thenReturn(Arrays.asList(
                member("600519.SH", "POINT_IN_TIME"), member("000001.SZ", "POINT_IN_TIME")));
        when(marketData.latestBarDate(7L, "600519.SH")).thenReturn(null);
        when(marketData.latestBarDate(7L, "000001.SZ")).thenReturn(null);
        when(source.fetch("600519.SH", 1000)).thenReturn(batch("600519.SH", "FRESH_PRIMARY"));
        when(source.fetch("000001.SZ", 1000)).thenThrow(
                new ProviderContractException("TIMEOUT", "upstream timeout", true));
        QuantDataSyncRun running = run("RUNNING", 2, 0, 0, 0, 0);
        when(runs.start(eq(7L), eq("MANUAL"), eq(2), any(LocalDateTime.class))).thenReturn(running);
        QuantDataSyncRun partial = run("PARTIAL", 2, 1, 1, 1, 0);
        when(runs.finish(eq(11L), eq("PARTIAL"), eq(1), eq(1), eq(1), eq(0),
                eq("EASTMONEY_DIRECT"), any(String.class), any(LocalDateTime.class))).thenReturn(partial);

        QuantDataSyncRun result = service(datasets, marketData, runs, source).sync(7L, "MANUAL");

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(1, result.getInsertedRows());
        verify(datasets).importBars(eq(7L), any());
    }

    @Test
    void filtersAtThePersistentWatermarkSoRerunsAreIdempotent() {
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantMarketDataRepository marketData = mock(QuantMarketDataRepository.class);
        QuantDataSyncRunRepository runs = mock(QuantDataSyncRunRepository.class);
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        when(datasets.get(7L)).thenReturn(eligibleDataset());
        when(marketData.findUniverseMembers(7L)).thenReturn(
                Collections.singletonList(member("600519.SH", "POINT_IN_TIME")));
        when(marketData.latestBarDate(7L, "600519.SH")).thenReturn(LocalDate.of(2026, 7, 16));
        when(source.fetch("600519.SH", 1000)).thenReturn(batch("600519.SH", "FRESH_PRIMARY"));
        when(runs.start(eq(7L), eq("SCHEDULED"), eq(1), any(LocalDateTime.class)))
                .thenReturn(run("RUNNING", 1, 0, 0, 0, 0));
        when(runs.finish(eq(11L), eq("SUCCESS"), eq(1), eq(0), eq(0), eq(0),
                eq("EASTMONEY_DIRECT"), eq(null), any(LocalDateTime.class)))
                .thenReturn(run("SUCCESS", 1, 1, 0, 0, 0));

        QuantDataSyncRun result = service(datasets, marketData, runs, source).sync(7L, "SCHEDULED");

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(0, result.getInsertedRows());
        verify(datasets, never()).importBars(any(), any());
    }

    @Test
    void rejectsFrozenLearningOrNonPointInTimeDatasetsBeforeStartingRun() {
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantMarketDataRepository marketData = mock(QuantMarketDataRepository.class);
        QuantDataSyncRunRepository runs = mock(QuantDataSyncRunRepository.class);
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        QuantDataset ready = eligibleDataset();
        ready.setStatus("READY");
        when(datasets.get(7L)).thenReturn(ready);
        QuantMarketDataSyncService service = service(datasets, marketData, runs, source);

        assertThrows(BusinessException.class, () -> service.sync(7L, "MANUAL"));

        ready.setStatus("BUILDING");
        when(marketData.findUniverseMembers(7L)).thenReturn(
                Collections.singletonList(member("600519.SH", "CURRENT_SNAPSHOT")));
        assertThrows(BusinessException.class, () -> service.sync(7L, "MANUAL"));
        verify(runs, never()).start(any(), any(), any(Integer.class), any(LocalDateTime.class));
    }

    private static QuantMarketDataSyncService service(QuantDatasetService datasets,
                                                       QuantMarketDataRepository marketData,
                                                       QuantDataSyncRunRepository runs,
                                                       QuantDailyBarSource source) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC);
        return new QuantMarketDataSyncService(datasets, marketData, runs, source, clock);
    }

    private static QuantDataset eligibleDataset() {
        QuantDataset value = new QuantDataset();
        value.setId(7L);
        value.setDataKind("REAL");
        value.setDatasetLevel("RESEARCH");
        value.setFingerprintVersion("quant-dataset-v2");
        value.setStatus("BUILDING");
        return value;
    }

    private static QuantUniverseMember member(String code, String sourceKind) {
        QuantUniverseMember value = new QuantUniverseMember();
        value.setDatasetId(7L);
        value.setTradeDate(LocalDate.of(2026, 7, 16));
        value.setInstrumentCode(code);
        value.setMember(true);
        value.setSourceKind(sourceKind);
        return value;
    }

    private static QuantDailyBarBatch batch(String code, String quality) {
        QuantDailyBar value = new QuantDailyBar();
        value.setInstrumentCode(code);
        value.setTradeDate(LocalDate.of(2026, 7, 16));
        value.setOpen(new BigDecimal("10"));
        value.setHigh(new BigDecimal("11"));
        value.setLow(new BigDecimal("9"));
        value.setClose(new BigDecimal("10.5"));
        value.setAdjustedClose(value.getClose());
        value.setVolume(new BigDecimal("100"));
        value.setAmount(new BigDecimal("1000"));
        value.setTradeStatus("TRADING");
        return new QuantDailyBarBatch(Collections.singletonList(value),
                "EASTMONEY_DIRECT", "EASTMONEY", quality,
                value.getTradeDate(), Collections.<String>emptyList());
    }

    private static QuantDataSyncRun run(String status, int requested, int succeeded,
                                        int failed, int inserted, int degraded) {
        return new QuantDataSyncRun(11L, 7L, "MANUAL", status, requested,
                succeeded, failed, inserted, degraded, "EASTMONEY_DIRECT", null,
                LocalDateTime.of(2026, 7, 20, 14, 0),
                "RUNNING".equals(status) ? null : LocalDateTime.of(2026, 7, 20, 14, 1));
    }
}
