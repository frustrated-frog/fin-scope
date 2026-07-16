package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestRequest;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.service.factorresearch.CapitalFlowFactorProvider;
import com.finscope.service.factorresearch.FactorProvider;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import com.finscope.service.factorresearch.LegacyQuantFactorProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantBacktestEngineTest {
    @Test
    void usesOnlyFrozenCapitalRowsAvailableBeforeNextOpen() {
        List<LocalDate> dates = tradingDates(4);
        BacktestRequest request = new BacktestRequest(); request.setDatasetId("1"); request.setInitialCapital(100000);
        QuantStrategySpec spec = spec(); spec.setFactors(Arrays.asList(
                new QuantStrategySpec.FactorWeight("MAIN_FLOW_SHARE", 1, "HIGH")));
        spec.getFilters().setMinTradingDays(0); spec.getPortfolio().setRebalanceEvery(1);
        request.setSpec(spec); request.setBars(bars(dates));
        List<QuantCapitalFlowDaily> flows = new ArrayList<QuantCapitalFlowDaily>();
        for (int i = 0; i < dates.size() - 1; i++) {
            flows.add(flow("600001.SH", dates.get(i), new BigDecimal("0.20"), dates.get(i).atTime(18, 0)));
            flows.add(flow("600002.SH", dates.get(i), new BigDecimal("0.10"), dates.get(i).atTime(18, 0)));
        }
        flows.get(0).setAvailableAt(dates.get(1).atTime(10, 0));
        request.setCapitalFlows(flows);
        FactorProviderRegistry providers = new FactorProviderRegistry(Arrays.<FactorProvider>asList(
                new LegacyQuantFactorProvider(), new CapitalFlowFactorProvider()));

        BacktestResult result = new QuantBacktestEngine(providers).run(request);

        assertFalse(result.getTrades().isEmpty());
        assertEquals("600002.SH", result.getTrades().get(0).getInstrumentCode());
        assertEquals(dates.get(1), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void executesCloseSignalOnlyAtNextOpenAndIsDeterministic() {
        List<LocalDate> dates = tradingDates(32);
        BacktestRequest request = new BacktestRequest();
        request.setInitialCapital(100000);
        request.setSpec(spec());
        request.setBars(bars(dates));
        QuantBacktestEngine engine = new QuantBacktestEngine();

        BacktestResult first = engine.run(request);
        BacktestResult second = engine.run(request);

        assertFalse(first.getTrades().isEmpty());
        assertEquals(dates.get(20), first.getTrades().get(0).getSignalDate());
        assertEquals(dates.get(21), first.getTrades().get(0).getTradeDate());
        assertEquals("600001.SH", first.getTrades().get(0).getInstrumentCode());
        assertEquals(first.getMetrics().getTotalReturn(), second.getMetrics().getTotalReturn(), 0.000000001);
        assertEquals(first.getEquityCurve().size(), second.getEquityCurve().size());
    }

    @Test
    void appliesPointInTimeUniverseToSelectionAndBenchmark() {
        List<LocalDate> dates = tradingDates(32);
        BacktestRequest request = new BacktestRequest(); request.setInitialCapital(100000);
        request.setSpec(spec()); request.setBars(bars(dates));
        List<QuantUniverseMember> universe = new ArrayList<QuantUniverseMember>();
        QuantUniverseMember member = new QuantUniverseMember(); member.setTradeDate(dates.get(0));
        member.setInstrumentCode("600002.SH"); member.setMember(true); member.setSourceKind("POINT_IN_TIME"); universe.add(member);
        QuantUniverseMember fastIn = new QuantUniverseMember(); fastIn.setTradeDate(dates.get(0)); fastIn.setInstrumentCode("600001.SH");
        fastIn.setMember(true); fastIn.setSourceKind("POINT_IN_TIME"); universe.add(fastIn);
        QuantUniverseMember fastOut = new QuantUniverseMember(); fastOut.setTradeDate(dates.get(20)); fastOut.setInstrumentCode("600001.SH");
        fastOut.setMember(false); fastOut.setSourceKind("POINT_IN_TIME"); universe.add(fastOut);
        request.setUniverse(universe);
        BacktestResult result = new QuantBacktestEngine().run(request);
        assertFalse(result.getTrades().isEmpty());
        assertEquals("600002.SH", result.getTrades().get(0).getInstrumentCode());
        assertTrue(result.getWarnings().stream().noneMatch(value -> value.contains("未提供时点股票池")));
    }

    @Test
    void carriesLastCloseWhenHeldInstrumentHasNoBar() {
        List<LocalDate> dates = tradingDates(34); List<QuantDailyBar> values = bars(dates);
        values.removeIf(value -> "600001.SH".equals(value.getInstrumentCode()) && dates.get(22).equals(value.getTradeDate()));
        BacktestRequest request = new BacktestRequest(); request.setInitialCapital(100000);
        request.setSpec(spec()); request.setBars(values);
        BacktestResult result = new QuantBacktestEngine().run(request);
        assertTrue(result.getWarnings().stream().anyMatch(value -> value.contains("沿用上一有效收盘价")), result.getWarnings().toString());
        assertTrue(result.getEquityCurve().stream().filter(value -> dates.get(22).equals(value.getTradeDate()))
                .allMatch(value -> value.getPortfolioNav() > 0.8));
    }

    @Test
    void keepsPreRangeHistoryForFactorWarmupButStartsEquityAtRequestedDate() {
        List<LocalDate> dates = tradingDates(32); BacktestRequest request = new BacktestRequest(); request.setInitialCapital(100000);
        QuantStrategySpec spec = spec(); spec.setStartDate(dates.get(20)); spec.setEndDate(dates.get(31)); request.setSpec(spec); request.setBars(bars(dates));
        BacktestResult result = new QuantBacktestEngine().run(request);
        assertEquals(dates.get(20), result.getEquityCurve().get(0).getTradeDate()); assertEquals(12, result.getEquityCurve().size());
        assertEquals(dates.get(20), result.getTrades().get(0).getSignalDate()); assertEquals(dates.get(21), result.getTrades().get(0).getTradeDate());
    }

    private QuantStrategySpec spec() {
        QuantStrategySpec spec = new QuantStrategySpec(); spec.setName("动量测试"); spec.setDatasetId(1L);
        spec.setBenchmark("EQUAL_WEIGHT"); spec.setInvestmentHypothesis("动量延续"); spec.setRiskBoundary("仅作历史研究");
        spec.setFactors(Arrays.asList(new QuantStrategySpec.FactorWeight("MOMENTUM_20D", 1, "HIGH")));
        QuantStrategySpec.Portfolio portfolio = new QuantStrategySpec.Portfolio();
        portfolio.setTopN(1); portfolio.setRebalanceEvery(20); portfolio.setWeighting("EQUAL"); spec.setPortfolio(portfolio);
        QuantStrategySpec.Filters filters = new QuantStrategySpec.Filters();
        filters.setExcludeSt(true); filters.setMinTradingDays(20); filters.setMinAmount(0); spec.setFilters(filters);
        QuantStrategySpec.Execution execution = new QuantStrategySpec.Execution();
        execution.setSignalPrice("CLOSE"); execution.setFillPrice("NEXT_OPEN"); execution.setSlippageBps(0); spec.setExecution(execution);
        QuantStrategySpec.Cost cost = new QuantStrategySpec.Cost(); spec.setCost(cost); return spec;
    }

    private List<QuantDailyBar> bars(List<LocalDate> dates) {
        List<QuantDailyBar> values = new ArrayList<QuantDailyBar>();
        for (int i = 0; i < dates.size(); i++) {
            values.add(bar("600001.SH", dates.get(i), 100 + i * 2));
            values.add(bar("600002.SH", dates.get(i), 100 + i * 0.2));
        }
        return values;
    }

    private QuantDailyBar bar(String code, LocalDate date, double price) {
        QuantDailyBar value = new QuantDailyBar(); value.setDatasetId(1L); value.setInstrumentCode(code); value.setTradeDate(date);
        value.setOpen(BigDecimal.valueOf(price)); value.setHigh(BigDecimal.valueOf(price * 1.01));
        value.setLow(BigDecimal.valueOf(price * 0.99)); value.setClose(BigDecimal.valueOf(price));
        value.setAdjustedClose(BigDecimal.valueOf(price)); value.setVolume(BigDecimal.valueOf(100000));
        value.setAmount(BigDecimal.valueOf(price * 100000)); value.setTradeStatus("TRADING"); return value;
    }

    private QuantCapitalFlowDaily flow(String code, LocalDate date, BigDecimal share,
                                       java.time.LocalDateTime availableAt) {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily(); value.setDatasetId(1L);
        value.setInstrumentCode(code); value.setTradeDate(date); value.setAvailableAt(availableAt);
        value.setSourceFlowId(1L); value.setProviderCode("TEST"); value.setAmount(new BigDecimal("100"));
        value.setMainNetInflow(share.multiply(new BigDecimal("100"))); value.setQualityStatus("COMPLETE");
        value.setSourceFingerprint(code + date); value.setCalculationVersion("v1"); return value;
    }

    private List<LocalDate> tradingDates(int count) {
        List<LocalDate> dates = new ArrayList<LocalDate>(); LocalDate date = LocalDate.of(2024, 1, 2);
        while (dates.size() < count) {
            if (date.getDayOfWeek().getValue() <= 5) dates.add(date);
            date = date.plusDays(1);
        }
        return dates;
    }
}
