package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockMarketHistory;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quote.PythonDailyBarClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockMarketHistoryServiceTest {
    private InstrumentRepository instruments;
    private PythonDailyBarClient client;
    private StockMarketHistoryService service;

    @BeforeEach
    void setUp() {
        instruments = mock(InstrumentRepository.class);
        client = mock(PythonDailyBarClient.class);
        service = new StockMarketHistoryService();
        ReflectionTestUtils.setField(service, "instruments", instruments);
        ReflectionTestUtils.setField(service, "dailyBars", client);
        Instrument stock = new Instrument();
        stock.setCode("600519");
        stock.setMarket("SH");
        stock.setType("STOCK");
        when(instruments.findByCodeAndType("600519", "STOCK")).thenReturn(Optional.of(stock));
    }

    @Test
    void readsStockAndExplicitShanghaiBenchmarkWithoutMixingSeries() {
        DailyBarHistory stock = history("QFQ");
        DailyBarHistory benchmark = history("QFQ");
        when(client.fetchHistory("600519", "SH", 500, false)).thenReturn(stock);
        when(client.fetchHistory("000300", "SH", 500, false)).thenReturn(benchmark);
        StockMarketHistory result = service.load("600519", false);
        assertSame(stock, result.getStock());
        assertSame(benchmark, result.getBenchmark());
        assertEquals("000300.SH", result.getBenchmarkCode());
    }

    @Test
    void refreshesLegacyAdjustmentOnceAndRetainsWarningsIfItCannotRecover() {
        when(client.fetchHistory("600519", "SH", 500, false)).thenReturn(history("NONE"));
        when(client.fetchHistory("600519", "SH", 500, true)).thenReturn(history("NONE"));
        when(client.fetchHistory("000300", "SH", 500, false)).thenReturn(history("QFQ"));
        StockMarketHistory result = service.load("600519", false);
        assertTrue(result.getStock().getWarnings().get(0).contains("未获得一致前复权"));
        verify(client, times(1)).fetchHistory("600519", "SH", 500, true);
    }

    @Test
    void benchmarkFailurePreservesStockAndExplicitlyExplainsMissingComparison() {
        DailyBarHistory stock = history("QFQ");
        when(client.fetchHistory("600519", "SH", 500, false)).thenReturn(stock);
        when(client.fetchHistory("000300", "SH", 500, false))
                .thenThrow(new ProviderContractException("OFFLINE", "offline", true));
        StockMarketHistory result = service.load("600519", false);
        assertSame(stock, result.getStock());
        assertNull(result.getBenchmark());
        assertTrue(result.getWarnings().get(0).contains("相对强弱对比未计算"));
    }

    @Test
    void adjustmentRefreshFailureKeepsOriginalHistoryAndExplainsSuppressedAnalytics() {
        DailyBarHistory original = history("NONE");
        when(client.fetchHistory("600519", "SH", 500, false)).thenReturn(original);
        when(client.fetchHistory("600519", "SH", 500, true))
                .thenThrow(new ProviderContractException("OFFLINE", "offline", true));
        when(client.fetchHistory("000300", "SH", 500, false)).thenReturn(history("QFQ"));
        StockMarketHistory result = service.load("600519", false);
        assertSame(original, result.getStock());
        assertTrue(result.getStock().getWarnings().stream().anyMatch(value -> value.contains("刷新失败")));
        assertTrue(result.getStock().getWarnings().stream().anyMatch(value -> value.contains("未获得一致前复权")));
    }

    @Test
    void rejectsMalformedAndFundOnlyCodesWithoutCallingProvider() {
        assertThrows(BusinessException.class, () -> service.load("../600519", false));
        when(instruments.findByCodeAndType("021894", "STOCK")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.load("021894", false));
        verifyNoInteractions(client);
    }

    @Test
    void preservesReadOnlyStockDrillDownFromAnUnregisteredHolding() {
        when(client.fetchHistory("688012", "SH", 500, false)).thenReturn(history("QFQ"));
        when(client.fetchHistory("000300", "SH", 500, false)).thenReturn(history("QFQ"));
        assertNotNull(service.load("688012", false).getStock());
        verify(instruments, never()).save(any());
    }

    private static DailyBarHistory history(String adjustment) {
        DailyBarPoint bar = new DailyBarPoint();
        bar.setAdjustment(adjustment);
        DailyBarHistory result = new DailyBarHistory();
        result.setBars(Collections.singletonList(bar));
        return result;
    }
}
