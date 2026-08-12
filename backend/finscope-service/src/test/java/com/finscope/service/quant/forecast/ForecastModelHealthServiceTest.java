package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForecastModelHealthServiceTest {
    @Test
    void keepsTheGateOpenUntilThereAreEightRealOutcomes() {
        ForecastModelHealth health = health(outcomes(7, 0.7, true));

        assertEquals("INSUFFICIENT_EVIDENCE", health.getStatus());
        assertFalse(health.isDirectionOutputPaused());
        assertEquals(7, health.getSampleCount());
    }

    @Test
    void pausesDirectionWhenProbabilityAndCoveredAccuracyDecay() {
        ForecastModelHealth health = health(outcomes(10, 0.8, false));

        assertEquals("PAUSED", health.getStatus());
        assertTrue(health.isDirectionOutputPaused());
        assertTrue(health.getBrierScore() > health.getBaselineBrierScore());
        assertEquals(0d, health.getCoveredAccuracy(), 0.000001d);
    }

    @Test
    void reportsHealthyAndCanRecoverUsingOnlyTheLatestTwentyOutcomes() {
        List<SingleStockForecastRun> values = outcomes(10, 0.8, false);
        List<SingleStockForecastRun> recovered = outcomes(20, 0.8, true);
        recovered.forEach(item -> item.setAsOfDate(item.getAsOfDate().plusDays(30)));
        values.addAll(recovered);

        ForecastModelHealth health = health(values);

        assertEquals("HEALTHY", health.getStatus());
        assertFalse(health.isDirectionOutputPaused());
        assertEquals(20, health.getSampleCount());
        assertEquals(1d, health.getCoveredAccuracy(), 0.000001d);
    }

    @Test
    void evaluatesShadowDirectionWhilePublicDirectionIsPaused() {
        List<SingleStockForecastRun> values = outcomes(8, 0.8, true);
        values.forEach(run -> {
            run.getReport().setModelDecision("UP");
            run.getReport().setDecision("ABSTAIN");
        });

        ForecastModelHealth health = health(values);

        assertEquals("HEALTHY", health.getStatus());
        assertEquals(8, health.getCoveredCount());
        assertEquals(1d, health.getCoveredAccuracy(), 0.000001d);
    }

    private ForecastModelHealth health(List<SingleStockForecastRun> values) {
        SingleStockForecastRunRepository repository = mock(SingleStockForecastRunRepository.class);
        when(repository.findHealthEvidence("603618.SH", 5, "model-v1", 20)).thenReturn(values);
        return new ForecastModelHealthService(repository).evaluate("603618.SH", 5, "model-v1");
    }

    private List<SingleStockForecastRun> outcomes(int count, double probability, boolean actualUp) {
        List<SingleStockForecastRun> values = new ArrayList<SingleStockForecastRun>();
        for (int index = 0; index < count; index++) {
            SingleStockForecastRun run = new SingleStockForecastRun();
            run.setId((long) index + 1);
            run.setInstrumentCode("603618.SH");
            run.setAsOfDate(LocalDate.of(2026, 1, 1).plusDays(index));
            run.setHorizonDays(5);
            run.setModelVersion("model-v1");
            run.setUpProbability(probability);
            SingleStockForecast report = new SingleStockForecast();
            report.setDecision(probability >= .6 ? "UP" : probability <= .4 ? "DOWN" : "ABSTAIN");
            run.setReport(report);
            SingleStockForecastRun.ForecastOutcome outcome = new SingleStockForecastRun.ForecastOutcome();
            outcome.setActualDirection(actualUp ? "UP" : "DOWN");
            outcome.setActualNetReturn(actualUp ? .02 : -.02);
            outcome.setCorrect(report.getDecision().equals(outcome.getActualDirection()));
            run.setOutcome(outcome);
            values.add(run);
        }
        return values;
    }
}
