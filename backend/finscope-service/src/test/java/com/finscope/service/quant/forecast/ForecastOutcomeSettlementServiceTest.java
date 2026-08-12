package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastOutcomeSettlementServiceTest {
    @Test
    void settlesUsingTradingDayIndicesAndFrozenRoundTripCost() {
        SingleStockForecastRunRepository repository = mock(SingleStockForecastRunRepository.class);
        SingleStockForecastRun run = run("UP", 5, 0.0015d);
        run.setReport(null);
        run.setReportJson("{\"decision\":\"UP\",\"strategyPolicy\":{\"roundTripCostRate\":0.0015}}");
        when(repository.findPending("603618.SH", 200)).thenReturn(Collections.singletonList(run));
        when(repository.settle(eq(7L), any())).thenReturn(true);
        QuantDailyBarSource bars = (code, limit) -> batch(Arrays.asList(
                bar("2026-08-07", 9.8), bar("2026-08-10", 10), bar("2026-08-11", 10.2),
                bar("2026-08-12", 10.3), bar("2026-08-13", 10.4), bar("2026-08-14", 10.6),
                bar("2026-08-17", 11)));

        ForecastOutcomeSettlementService service = new ForecastOutcomeSettlementService(repository, bars);
        ForecastOutcomeSettlementService.SettlementSummary summary = service.settlePending("603618.SH");

        org.mockito.ArgumentCaptor<SingleStockForecastRun.ForecastOutcome> outcome =
                org.mockito.ArgumentCaptor.forClass(SingleStockForecastRun.ForecastOutcome.class);
        verify(repository).settle(eq(7L), outcome.capture());
        assertEquals(LocalDate.of(2026, 8, 10), outcome.getValue().getEntryDate());
        assertEquals(LocalDate.of(2026, 8, 17), outcome.getValue().getExitDate());
        assertEquals(0.0985d, outcome.getValue().getActualNetReturn(), 0.000001d);
        assertEquals("UP", outcome.getValue().getActualDirection());
        assertTrue(outcome.getValue().getCorrect());
        assertEquals(1, summary.getMatured());
    }

    @Test
    void abstainIsSettledWithoutPretendingItWasCorrect() {
        SingleStockForecastRunRepository repository = mock(SingleStockForecastRunRepository.class);
        SingleStockForecastRun run = run("ABSTAIN", 1, 0.0015d);
        when(repository.findPending("603618.SH", 200)).thenReturn(Collections.singletonList(run));
        QuantDailyBarSource bars = (code, limit) -> batch(Arrays.asList(
                bar("2026-08-07", 10), bar("2026-08-10", 10), bar("2026-08-11", 9)));

        new ForecastOutcomeSettlementService(repository, bars).settlePending("603618.SH");

        org.mockito.ArgumentCaptor<SingleStockForecastRun.ForecastOutcome> outcome =
                org.mockito.ArgumentCaptor.forClass(SingleStockForecastRun.ForecastOutcome.class);
        verify(repository).settle(eq(7L), outcome.capture());
        assertEquals("DOWN", outcome.getValue().getActualDirection());
        assertEquals(null, outcome.getValue().getCorrect());
    }

    @Test
    void keepsRunPendingUntilEnoughFutureTradingBarsExist() {
        SingleStockForecastRunRepository repository = mock(SingleStockForecastRunRepository.class);
        when(repository.findPending("603618.SH", 200)).thenReturn(Collections.singletonList(run("DOWN", 5, .0015)));
        QuantDailyBarSource bars = (code, limit) -> batch(Arrays.asList(
                bar("2026-08-07", 10), bar("2026-08-10", 10), bar("2026-08-11", 9.8)));

        ForecastOutcomeSettlementService.SettlementSummary summary =
                new ForecastOutcomeSettlementService(repository, bars).settlePending("603618.SH");

        verify(repository, never()).settle(eq(7L), any());
        verify(repository, never()).markUnavailable(eq(7L), any());
        assertEquals(1, summary.getPending());
    }

    private SingleStockForecastRun run(String decision, int horizon, double cost) {
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setId(7L);
        value.setInstrumentCode("603618.SH");
        value.setAsOfDate(LocalDate.of(2026, 8, 7));
        value.setHorizonDays(horizon);
        value.setMaturityStatus(SingleStockForecastRun.MaturityStatus.PENDING);
        SingleStockForecast report = new SingleStockForecast();
        report.setDecision(decision);
        SingleStockForecast.StrategyPolicy policy = new SingleStockForecast.StrategyPolicy();
        policy.setRoundTripCostRate(cost);
        report.setStrategyPolicy(policy);
        value.setReport(report);
        return value;
    }

    private QuantDailyBarBatch batch(List<QuantDailyBar> values) {
        return new QuantDailyBarBatch(values, "PYTDX", "TDX", "FRESH_PRIMARY",
                values.get(values.size() - 1).getTradeDate(), Collections.emptyList());
    }

    private QuantDailyBar bar(String date, double open) {
        QuantDailyBar value = new QuantDailyBar();
        value.setTradeDate(LocalDate.parse(date));
        value.setOpen(BigDecimal.valueOf(open));
        return value;
    }
}
