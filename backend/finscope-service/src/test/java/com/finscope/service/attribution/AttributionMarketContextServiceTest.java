package com.finscope.service.attribution;

import com.finscope.domain.attribution.AttributionMarketContext;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.quote.PythonDailyBarClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttributionMarketContextServiceTest {
    @Test
    void calculatesAlignedReturnsWithoutUsingFutureBars() {
        PythonDailyBarClient client = mock(PythonDailyBarClient.class);
        when(client.fetchDailyBars("600519", 250)).thenReturn(Arrays.asList(bar("2026-09-17", 100), bar("2026-09-18", 106), bar("2026-09-21", 200)));
        when(client.fetchMarketBenchmark(250)).thenReturn(Arrays.asList(bar("2026-09-17", 100), bar("2026-09-18", 104), bar("2026-09-21", 200)));
        AttributionMarketContextService service = new AttributionMarketContextService();
        ReflectionTestUtils.setField(service, "dailyBarClient", client);
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        AttributionMarketContext result = service.capture(instrument, LocalDate.parse("2026-09-18"));
        assertTrue(result.isQuoteVerified());
        assertEquals(6D, result.getStockChangePct(), 0.00001);
        assertEquals(4D, result.getBenchmarkChangePct(), 0.00001);
        assertEquals(2D, result.getRelativeChangePct(), 0.00001);
        assertNull(result.getPriorFiveSessionChangePct());
        assertFalse(service.capture(instrument, LocalDate.parse("2026-09-19")).isQuoteVerified());
        assertNull(service.capture(instrument, LocalDate.parse("2026-09-19")).getRelativeChangePct());
    }

    @Test
    void unavailableSourcesLeaveExplicitGaps() {
        PythonDailyBarClient client = mock(PythonDailyBarClient.class);
        when(client.fetchDailyBars("600519", 250)).thenThrow(new IllegalStateException());
        when(client.fetchMarketBenchmark(250)).thenThrow(new IllegalStateException());
        AttributionMarketContextService service = new AttributionMarketContextService();
        ReflectionTestUtils.setField(service, "dailyBarClient", client);
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        AttributionMarketContext result = service.capture(instrument, LocalDate.parse("2026-09-18"));
        assertFalse(result.isQuoteVerified());
        assertNull(result.getStockChangePct());
        assertNull(result.getBenchmarkChangePct());
        assertTrue(result.getLimitations().stream().anyMatch(text -> text.contains("前端数值")));
    }

    private DailyBarPoint bar(String date, int close) {
        DailyBarPoint bar = new DailyBarPoint();
        bar.setTradeDate(LocalDate.parse(date));
        bar.setClose(BigDecimal.valueOf(close));
        return bar;
    }
}
