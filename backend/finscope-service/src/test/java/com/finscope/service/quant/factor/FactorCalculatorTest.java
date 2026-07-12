package com.finscope.service.quant.factor;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorCalculatorTest {
    @Test
    void exposesThirteenAuditableFactors() {
        FactorRegistry registry = new FactorRegistry();
        assertEquals(13, registry.list().size());
        assertEquals("价值", registry.get("EP").getCategory());
        assertTrue(registry.get("ROE").isPointInTime());
        assertFalse(registry.get("MOMENTUM_20D").isPointInTime());
    }

    @Test
    void calculatesPriceAndFundamentalFactors() {
        FactorCalculator calculator = new FactorCalculator();
        List<QuantDailyBar> history = history(61);
        QuantFundamentalSnapshot fundamental = new QuantFundamentalSnapshot();
        fundamental.setPe(new BigDecimal("20")); fundamental.setPb(new BigDecimal("4"));
        fundamental.setRoe(new BigDecimal("0.16")); fundamental.setDebtRatio(new BigDecimal("0.40"));
        fundamental.setMarketCap(new BigDecimal("10000000000"));
        fundamental.setRevenueGrowth(new BigDecimal("0.12")); fundamental.setProfitGrowth(new BigDecimal("0.18"));

        assertEquals(160d / 140d - 1d, calculator.value("MOMENTUM_20D", history, fundamental), 0.000001);
        assertEquals(0.6, calculator.value("MOMENTUM_60D", history, fundamental), 0.000001);
        assertEquals(0.05, calculator.value("EP", history, fundamental), 0.000001);
        assertEquals(-0.40, calculator.value("LOW_DEBT", history, fundamental), 0.000001);
    }

    @Test
    void winsorizesAndNormalizesOneCrossSection() {
        FactorPreprocessor preprocessor = new FactorPreprocessor();
        Map<String, Double> normalized = preprocessor.normalize(Arrays.asList(
                value("A", 1), value("B", 2), value("C", 3), value("D", 100)));

        assertEquals(4, normalized.size());
        double mean = normalized.values().stream().mapToDouble(Double::doubleValue).average().orElse(1);
        assertEquals(0, mean, 0.000001);
        assertTrue(normalized.get("D") < 2);
    }

    @Test
    void turnoverProxyKeepsPositiveVariationForRegisteredLowDirection() {
        FactorCalculator calculator = new FactorCalculator(); List<QuantDailyBar> stable = history(21); List<QuantDailyBar> volatileVolume = history(21);
        for (int i = 1; i < volatileVolume.size(); i += 2) volatileVolume.get(i).setVolume(BigDecimal.valueOf(300_000));
        assertTrue(calculator.value("TURNOVER_PROXY_20D", volatileVolume, null)
                > calculator.value("TURNOVER_PROXY_20D", stable, null));
        assertEquals("LOW", new FactorRegistry().get("TURNOVER_PROXY_20D").getDirection());
    }

    private com.finscope.domain.quant.factor.FactorValue value(String code, double value) {
        return new com.finscope.domain.quant.factor.FactorValue(LocalDate.of(2024, 1, 1), code, "TEST", value);
    }

    private List<QuantDailyBar> history(int size) {
        List<QuantDailyBar> values = new ArrayList<QuantDailyBar>();
        for (int i = 0; i < size; i++) {
            QuantDailyBar bar = new QuantDailyBar();
            bar.setInstrumentCode("600000.SH"); bar.setTradeDate(LocalDate.of(2024, 1, 1).plusDays(i));
            bar.setAdjustedClose(BigDecimal.valueOf(100 + i)); bar.setClose(bar.getAdjustedClose());
            bar.setAmount(BigDecimal.valueOf(1_000_000 + i * 10_000));
            bar.setVolume(BigDecimal.valueOf(100_000 + i * 1000));
            values.add(bar);
        }
        return values;
    }
}
