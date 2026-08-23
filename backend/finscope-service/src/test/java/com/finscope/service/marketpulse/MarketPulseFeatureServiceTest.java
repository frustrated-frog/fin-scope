package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketPulseFeatureServiceTest {
    @Test
    void resolvesTheLatestBusinessDateFromTheMarketBatch() {
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        LocalDate latestTradingDate = LocalDate.of(2026, 8, 21);
        when(source.fetch("000300.SH", 180)).thenReturn(batch(latestTradingDate));
        MarketPulseFeatureService service = new MarketPulseFeatureService();
        ReflectionTestUtils.setField(service, "dailyBarSource", source);

        assertEquals(latestTradingDate, service.latestBusinessDate());
    }

    @Test
    void calculatesPointInTimeIndexFeaturesWithoutUsingFutureBars() {
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        LocalDate businessDate = LocalDate.of(2026, 8, 21);
        when(source.fetch("000300.SH", 180)).thenReturn(batch(businessDate));
        MarketPulseFeatureService service = new MarketPulseFeatureService();
        ReflectionTestUtils.setField(service, "dailyBarSource", source);
        ReflectionTestUtils.setField(service, "classifier", new MarketRegimeClassifier());

        MarketRegimeSnapshot result = service.calculate(businessDate, 0.028D);

        assertEquals(businessDate, result.getBusinessDate());
        assertNotNull(result.getFeatures().getReturn20d());
        assertEquals(0.028D, result.getFeatures().getSectorDispersion());
        assertNotNull(result.getMarketStage());
    }

    @Test
    void returnsInsufficientDataWhenTheIndexHistoryIsTooShort() {
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        LocalDate businessDate = LocalDate.of(2026, 8, 21);
        when(source.fetch("000300.SH", 180)).thenReturn(new QuantDailyBarBatch(
                Collections.singletonList(bar(businessDate, 100D, 1000D)), "TEST", "TEST",
                "FRESH_PRIMARY", businessDate, Collections.emptyList()));
        MarketPulseFeatureService service = new MarketPulseFeatureService();
        ReflectionTestUtils.setField(service, "dailyBarSource", source);
        ReflectionTestUtils.setField(service, "classifier", new MarketRegimeClassifier());

        MarketRegimeSnapshot result = service.calculate(businessDate, 0.02D);

        assertEquals(MarketStage.INSUFFICIENT_DATA, result.getMarketStage());
    }

    private QuantDailyBarBatch batch(LocalDate businessDate) {
        List<QuantDailyBar> bars = new ArrayList<>();
        for (int index = 79; index >= 0; index--) {
            LocalDate date = businessDate.minusDays(index);
            bars.add(bar(date, 100D + (79 - index) * 0.2D, 1000D + (79 - index) * 5D));
        }
        bars.add(bar(businessDate.plusDays(1), 999D, 9999D));
        return new QuantDailyBarBatch(bars, "TEST", "TEST", "FRESH_PRIMARY", businessDate,
                Collections.emptyList());
    }

    private QuantDailyBar bar(LocalDate date, double close, double amount) {
        QuantDailyBar value = new QuantDailyBar();
        value.setTradeDate(date);
        value.setClose(BigDecimal.valueOf(close));
        value.setOpen(BigDecimal.valueOf(close));
        value.setHigh(BigDecimal.valueOf(close));
        value.setLow(BigDecimal.valueOf(close));
        value.setAmount(BigDecimal.valueOf(amount));
        value.setVolume(BigDecimal.TEN);
        return value;
    }
}
