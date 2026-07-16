package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorObservation;
import com.finscope.domain.factorresearch.ObservationQuality;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalFlowFactorProviderTest {
    private final CapitalFlowFactorProvider provider = new CapitalFlowFactorProvider();

    @Test
    void calculatesMainFlowShareFromFrozenExactDecimals() {
        QuantCapitalFlowDaily source = source(new BigDecimal("120000000"), new BigDecimal("800000000"));
        FactorObservation value = provider.calculate(context(source), CapitalFlowFactorProvider.MAIN_FLOW_SHARE);

        assertEquals(new BigDecimal("0.1500000000"), value.getProcessedValue());
        assertEquals(source.getAvailableAt(), value.getAvailableAt());
        assertEquals(source.getSourceFingerprint(), value.getSourceFingerprint());
        assertEquals(ObservationQuality.COMPLETE, value.getQualityStatus());
    }

    @Test
    void calculatesComparableLargeOrderSharesFromTheSameFrozenRow() {
        QuantCapitalFlowDaily source = source(new BigDecimal("120"), new BigDecimal("1000"));
        source.setSuperLargeNetInflow(new BigDecimal("80"));
        source.setLargeNetInflow(new BigDecimal("40"));

        assertEquals(new BigDecimal("0.0800000000"), provider.calculate(
                context(source), CapitalFlowFactorProvider.SUPER_LARGE_FLOW_SHARE).getProcessedValue());
        assertEquals(new BigDecimal("0.1200000000"), provider.calculate(
                context(source), CapitalFlowFactorProvider.BIG_ORDER_FLOW_SHARE).getProcessedValue());
    }

    @Test
    void doesNotPartiallyCalculateBigOrderShareWhenARequiredBucketIsMissing() {
        QuantCapitalFlowDaily source = source(new BigDecimal("120"), new BigDecimal("1000"));
        source.setSuperLargeNetInflow(new BigDecimal("80"));

        FactorObservation result = provider.calculate(
                context(source), CapitalFlowFactorProvider.BIG_ORDER_FLOW_SHARE);

        assertEquals(ObservationQuality.MISSING_INPUT, result.getQualityStatus());
        assertNull(result.getProcessedValue());
    }

    @Test
    void refusesZeroOrMissingAmount() {
        FactorObservation zero = provider.calculate(
                context(source(BigDecimal.TEN, BigDecimal.ZERO)), CapitalFlowFactorProvider.MAIN_FLOW_SHARE);
        FactorObservation missing = provider.calculate(
                context(source(BigDecimal.TEN, null)), CapitalFlowFactorProvider.MAIN_FLOW_SHARE);

        assertEquals(ObservationQuality.MISSING_INPUT, zero.getQualityStatus());
        assertEquals(ObservationQuality.MISSING_INPUT, missing.getQualityStatus());
        assertNull(zero.getProcessedValue());
    }

    @Test
    void refusesSourceThatArrivedAfterExecutionCutoff() {
        QuantCapitalFlowDaily source = source(BigDecimal.TEN, new BigDecimal("100"));
        FactorCalculationContext beforeArrival = new FactorCalculationContext("7", "600519.SH",
                source.getTradeDate(), source.getAvailableAt().minusSeconds(1), null, null, source);

        FactorObservation value = provider.calculate(beforeArrival, CapitalFlowFactorProvider.MAIN_FLOW_SHARE);

        assertEquals(ObservationQuality.MISSING_INPUT, value.getQualityStatus());
        assertNull(value.getProcessedValue());
    }

    @Test
    void treatsMissingSourceIdentityAsMissingInputInsteadOfThrowing() {
        QuantCapitalFlowDaily source = source(BigDecimal.TEN, new BigDecimal("100"));
        source.setTradeDate(null);
        FactorCalculationContext context = new FactorCalculationContext("7", "600519.SH",
                LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 15, 9, 30), null, null, source);

        FactorObservation value = provider.calculate(context, CapitalFlowFactorProvider.MAIN_FLOW_SHARE);

        assertEquals(ObservationQuality.MISSING_INPUT, value.getQualityStatus());
    }

    @Test
    void calculatesStrictNormalizedFlowWindowsAndPriceDivergence() {
        List<com.finscope.domain.quant.data.QuantDailyBar> prices = new ArrayList<com.finscope.domain.quant.data.QuantDailyBar>();
        List<QuantCapitalFlowDaily> flows = new ArrayList<QuantCapitalFlowDaily>();
        LocalDate start = LocalDate.of(2026, 6, 1);
        for (int day = 0; day < 20; day++) {
            LocalDate date = start.plusDays(day);
            com.finscope.domain.quant.data.QuantDailyBar bar = new com.finscope.domain.quant.data.QuantDailyBar();
            bar.setTradeDate(date); bar.setAdjustedClose(BigDecimal.valueOf(100 + day)); prices.add(bar);
            QuantCapitalFlowDaily flow = source(BigDecimal.valueOf(day + 1), new BigDecimal("100"));
            flow.setTradeDate(date); flow.setAvailableAt(date.atTime(18, 0)); flow.setSourceFingerprint("flow-" + day); flows.add(flow);
        }
        QuantCapitalFlowDaily latest = flows.get(19);
        FactorCalculationContext context = new FactorCalculationContext("7", "600519.SH", latest.getTradeDate(),
                latest.getAvailableAt(), prices, null, latest, flows);

        assertEquals(new BigDecimal("0.9000000000"), provider.calculate(context,
                CapitalFlowFactorProvider.NORMALIZED_MAIN_FLOW_SUM_5D).getProcessedValue());
        assertEquals(new BigDecimal("1.0000000000"), provider.calculate(context,
                CapitalFlowFactorProvider.FLOW_PERSISTENCE_5D).getProcessedValue());
        assertEquals(new BigDecimal("1.6475089421"), provider.calculate(context,
                CapitalFlowFactorProvider.MAIN_FLOW_SHARE_ZSCORE_20D).getProcessedValue());
        assertTrue(provider.calculate(context, CapitalFlowFactorProvider.PRICE_FLOW_DIVERGENCE_5D)
                .getProcessedValue().signum() < 0);
    }

    @Test
    void refusesCompressedOrLateCapitalWindows() {
        List<com.finscope.domain.quant.data.QuantDailyBar> prices = new ArrayList<com.finscope.domain.quant.data.QuantDailyBar>();
        List<QuantCapitalFlowDaily> flows = new ArrayList<QuantCapitalFlowDaily>();
        LocalDate start = LocalDate.of(2026, 7, 1);
        for (int day = 0; day < 5; day++) {
            com.finscope.domain.quant.data.QuantDailyBar bar = new com.finscope.domain.quant.data.QuantDailyBar();
            bar.setTradeDate(start.plusDays(day)); bar.setAdjustedClose(BigDecimal.TEN); prices.add(bar);
            QuantCapitalFlowDaily flow = source(BigDecimal.ONE, BigDecimal.TEN); flow.setTradeDate(start.plusDays(day));
            flow.setAvailableAt(start.plusDays(day).atTime(18, 0)); flows.add(flow);
        }
        QuantCapitalFlowDaily latest = flows.get(4);
        FactorCalculationContext missingDay = new FactorCalculationContext("7", "600519.SH", latest.getTradeDate(), latest.getAvailableAt(),
                prices, null, latest, Arrays.asList(flows.get(0), flows.get(1), flows.get(3), flows.get(4)));
        assertEquals(ObservationQuality.MISSING_INPUT, provider.calculate(missingDay,
                CapitalFlowFactorProvider.NORMALIZED_MAIN_FLOW_SUM_5D).getQualityStatus());
        flows.get(2).setAvailableAt(latest.getAvailableAt().plusMinutes(1));
        FactorCalculationContext late = new FactorCalculationContext("7", "600519.SH", latest.getTradeDate(), latest.getAvailableAt(),
                prices, null, latest, flows);
        assertEquals(ObservationQuality.MISSING_INPUT, provider.calculate(late,
                CapitalFlowFactorProvider.NORMALIZED_MAIN_FLOW_SUM_5D).getQualityStatus());
    }

    private FactorCalculationContext context(QuantCapitalFlowDaily source) {
        return new FactorCalculationContext("7", "600519.SH", source.getTradeDate(),
                source.getAvailableAt(), null, null, source);
    }

    private QuantCapitalFlowDaily source(BigDecimal main, BigDecimal amount) {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily();
        value.setDatasetId(7L); value.setInstrumentCode("600519.SH");
        value.setTradeDate(LocalDate.of(2026, 7, 14));
        value.setAvailableAt(LocalDateTime.of(2026, 7, 14, 18, 0));
        value.setSourceFlowId(101L); value.setProviderCode("EASTMONEY");
        value.setMainNetInflow(main); value.setAmount(amount);
        value.setQualityStatus("COMPLETE"); value.setSourceFingerprint("payload");
        value.setCalculationVersion("capital-flow-v3");
        return value;
    }
}
