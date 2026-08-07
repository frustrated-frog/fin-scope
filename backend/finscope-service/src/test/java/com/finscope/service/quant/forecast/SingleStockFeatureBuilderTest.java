package com.finscope.service.quant.forecast;

import com.finscope.domain.quant.data.QuantDailyBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleStockFeatureBuilderTest {
    private final SingleStockFeatureBuilder builder = new SingleStockFeatureBuilder();

    @Test
    void buildsSignalFeaturesWithoutReadingFutureBars() {
        List<QuantDailyBar> bars = bars(100);
        List<ForecastSample> before = builder.build(bars, 0.002d);
        double[] signalFeatures = before.get(0).getFeatures();

        bars.get(70).setClose(new BigDecimal("9999"));
        bars.get(70).setAdjustedClose(new BigDecimal("9999"));
        List<ForecastSample> after = builder.build(bars, 0.002d);

        assertEquals(bars.get(60).getTradeDate(), before.get(0).getSignalDate());
        assertArrayEquals(signalFeatures, after.get(0).getFeatures(), 0.000000001d);
    }

    @Test
    void labelsExecutableReturnFromNextOpenToTwentiethCloseAfterCosts() {
        List<QuantDailyBar> bars = bars(100);
        bars.get(61).setOpen(new BigDecimal("100"));
        bars.get(80).setClose(new BigDecimal("110"));
        bars.get(80).setAdjustedClose(new BigDecimal("110"));

        ForecastSample sample = builder.build(bars, 0.002d).get(0);

        assertEquals(bars.get(61).getTradeDate(), sample.getEntryDate());
        assertEquals(bars.get(80).getTradeDate(), sample.getExitDate());
        assertEquals(0.098d, sample.getNetReturn(), 0.000000001d);
        assertTrue(sample.isPositive());
    }

    @Test
    void requiresSixtyBarsOfWarmupAndTwentyFutureBars() {
        assertTrue(builder.build(bars(80), 0.002d).isEmpty());
        assertEquals(1, builder.build(bars(81), 0.002d).size());
    }

    private List<QuantDailyBar> bars(int count) {
        List<QuantDailyBar> values = new ArrayList<QuantDailyBar>();
        LocalDate date = LocalDate.of(2018, 1, 2);
        for (int i = 0; i < count; i++) {
            double price = 80d + i * 0.35d + Math.sin(i / 5d);
            QuantDailyBar bar = new QuantDailyBar();
            bar.setInstrumentCode("600519.SH");
            bar.setTradeDate(date.plusDays(i));
            bar.setOpen(BigDecimal.valueOf(price - 0.2d));
            bar.setHigh(BigDecimal.valueOf(price + 1d));
            bar.setLow(BigDecimal.valueOf(price - 1d));
            bar.setClose(BigDecimal.valueOf(price));
            bar.setAdjustedClose(BigDecimal.valueOf(price));
            bar.setVolume(BigDecimal.valueOf(100000L + i * 100L));
            bar.setAmount(BigDecimal.valueOf((100000L + i * 100L) * price));
            bar.setTradeStatus("TRADING");
            values.add(bar);
        }
        return values;
    }
}
