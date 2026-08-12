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
}
