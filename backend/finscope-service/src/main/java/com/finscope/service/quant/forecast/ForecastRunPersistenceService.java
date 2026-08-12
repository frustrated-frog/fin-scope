package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.ForecastCandidateRunRepository;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.forecast.ForecastCandidateRun;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ForecastRunPersistenceService {
    private final SingleStockForecastRunRepository runs;
    private final ForecastCandidateRunRepository candidates;

    public ForecastRunPersistenceService(SingleStockForecastRunRepository runs,
                                         ForecastCandidateRunRepository candidates) {
        this.runs = runs;
        this.candidates = candidates;
    }

    @Transactional(rollbackFor = Exception.class)
    public SingleStockForecastRun save(SingleStockForecastRun run,
                                       List<ForecastCandidateRun> candidateRuns) {
        SingleStockForecastRun saved = runs.save(run);
        candidates.saveAll(saved.getId(), candidateRuns);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean settle(long forecastRunId, SingleStockForecastRun.ForecastOutcome outcome) {
        if (!runs.settle(forecastRunId, outcome)) {
            return false;
        }
        candidates.settleByForecastRunId(forecastRunId, outcome.getActualNetReturn(),
                outcome.getActualDirection(), outcome.getSettledAt());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markUnavailable(long forecastRunId, String note) {
        if (!runs.markUnavailable(forecastRunId, note)) {
            return false;
        }
        candidates.markUnavailableByForecastRunId(forecastRunId, java.time.LocalDateTime.now());
        return true;
    }
}
