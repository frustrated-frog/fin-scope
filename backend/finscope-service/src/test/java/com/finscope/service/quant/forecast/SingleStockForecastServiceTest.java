package com.finscope.service.quant.forecast;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleStockForecastServiceTest {
    @Test
    void returnsStructuredInsufficientStateWithoutInventingProbability() {
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        when(source.fetch("600519.SH", 5000)).thenReturn(batch(bars(400)));

        SingleStockForecast result = new SingleStockForecastService(source).forecast("600519");

        assertEquals("INSUFFICIENT_DATA", result.getStatus());
        assertEquals(400, result.getBarCount());
        assertEquals(null, result.getUpProbability());
        assertTrue(result.getConclusion().contains("不足"));
    }

    @Test
    void producesAuditableTwentyDayForecastFromLongServerHistory() {
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        when(source.fetch("600519.SH", 5000)).thenReturn(batch(bars(1600)));

        SingleStockForecast result = new SingleStockForecastService(source).forecast("600519.SH");

        verify(source).fetch("600519.SH", 5000);
        assertEquals("600519.SH", result.getInstrumentCode());
        assertEquals(20, result.getHorizonDays());
        assertTrue(result.getUpProbability() >= 0d && result.getUpProbability() <= 1d);
        assertNotNull(result.getExpectedNetReturn());
        assertTrue(result.getLowerNetReturn() <= result.getUpperNetReturn());
        assertNotNull(result.getDataFingerprint());
        assertEquals(64, result.getDataFingerprint().length());
        assertTrue(result.getValidation().getIndependentSampleCount() > 0);
        assertTrue(result.getRecentObservations().size() <= 12);
    }

    private QuantDailyBarBatch batch(List<QuantDailyBar> bars) {
        return new QuantDailyBarBatch(bars, "EASTMONEY_DIRECT", "EASTMONEY", "FRESH_PRIMARY",
                bars.get(bars.size() - 1).getTradeDate(), Collections.<String>emptyList());
    }

    private List<QuantDailyBar> bars(int count) {
        List<QuantDailyBar> values = new ArrayList<QuantDailyBar>();
        LocalDate first = LocalDate.of(2015, 1, 1);
        for (int i = 0; i < count; i++) {
            double trend = 80d + i * 0.025d;
            double price = trend + Math.sin(i / 17d) * 5d + Math.sin(i / 5d);
            QuantDailyBar bar = new QuantDailyBar();
            bar.setInstrumentCode("600519.SH"); bar.setTradeDate(first.plusDays(i));
            bar.setOpen(BigDecimal.valueOf(price * (1d + Math.sin(i) * 0.001d)));
            bar.setHigh(BigDecimal.valueOf(price * 1.02d)); bar.setLow(BigDecimal.valueOf(price * 0.98d));
            bar.setClose(BigDecimal.valueOf(price)); bar.setAdjustedClose(BigDecimal.valueOf(price));
            bar.setVolume(BigDecimal.valueOf(1000000L + (i % 30) * 10000L));
            bar.setAmount(BigDecimal.valueOf(price * (1000000L + (i % 30) * 10000L)));
            bar.setTradeStatus("TRADING"); values.add(bar);
        }
        return values;
    }
}
