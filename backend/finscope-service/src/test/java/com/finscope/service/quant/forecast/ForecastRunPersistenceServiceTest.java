package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.ForecastCandidateRunRepository;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastRunPersistenceServiceTest {
    @Test
    void settlesMainRunAndEveryCandidateWithOneFrozenOutcome() {
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        ForecastCandidateRunRepository candidates = mock(ForecastCandidateRunRepository.class);
        SingleStockForecastRun.ForecastOutcome outcome = outcome();
        when(runs.settle(7L, outcome)).thenReturn(true);

        boolean settled = new ForecastRunPersistenceService(runs, candidates).settle(7L, outcome);

        assertTrue(settled);
        verify(candidates).settleByForecastRunId(7L, .023d, "UP", outcome.getSettledAt());
    }

    @Test
    void doesNotTouchCandidatesWhenMainRunWasAlreadySettled() {
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        ForecastCandidateRunRepository candidates = mock(ForecastCandidateRunRepository.class);
        SingleStockForecastRun.ForecastOutcome outcome = outcome();
        when(runs.settle(7L, outcome)).thenReturn(false);

        boolean settled = new ForecastRunPersistenceService(runs, candidates).settle(7L, outcome);

        assertFalse(settled);
        verify(candidates, never()).settleByForecastRunId(7L, .023d, "UP", outcome.getSettledAt());
    }

    private SingleStockForecastRun.ForecastOutcome outcome() {
        SingleStockForecastRun.ForecastOutcome value = new SingleStockForecastRun.ForecastOutcome();
        value.setActualNetReturn(.023d);
        value.setActualDirection("UP");
        value.setSettledAt(LocalDateTime.of(2026, 8, 13, 18, 0));
        return value;
    }
}
