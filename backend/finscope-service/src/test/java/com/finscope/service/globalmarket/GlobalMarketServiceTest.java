package com.finscope.service.globalmarket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.rpc.globalmarket.GlobalMarketSource;
import com.finscope.service.marketdata.MarketDataSingleFlight;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.finscope.domain.marketdata.MarketDataCapability.GLOBAL_MARKET_SERIES;

class GlobalMarketServiceTest {
    @Test
    void failedRefreshKeepsLastSuccessfulTimestampAndRejectsUnknownIds() throws IOException {
        GlobalMarketService service = new GlobalMarketService();
        GlobalMarketSource source = mock(GlobalMarketSource.class);
        MarketDataSnapshotRepository repo = mock(MarketDataSnapshotRepository.class);
        ReflectionTestUtils.setField(service, "flights", new MarketDataSingleFlight());
        ReflectionTestUtils.setField(service, "source", source);
        ReflectionTestUtils.setField(service, "snapshots", repo);
        ReflectionTestUtils.setField(service, "json", new ObjectMapper());
        LocalDateTime time = LocalDateTime.of(2024, 1, 3, 0, 0);
        String data = """
                {"symbol":"BTC-USD","source":"Coinbase","asOf":"2024-01-03T00:00:00Z","price":100,
                "reference":90,"closes":{"2024-01-02":95}}
                """;
        when(repo.find(GLOBAL_MARKET_SERIES, "BTC")).thenReturn(Optional.of(new MarketDataSnapshot(
                GLOBAL_MARKET_SERIES, "BTC", "Coinbase", "Coinbase", time, time, data, "hash", 1, time)));
        when(source.fetch("BTC")).thenThrow(new IOException("unavailable"));
        GlobalMarketService.Result result = service.market("BTC");
        assertTrue(result.fallback());
        assertEquals(time.toInstant(ZoneOffset.UTC).toString(), result.retrievedAt());
        assertThrows(BusinessException.class, () -> service.market("US-../"));
        verify(repo, never()).upsert(any());
    }
}
