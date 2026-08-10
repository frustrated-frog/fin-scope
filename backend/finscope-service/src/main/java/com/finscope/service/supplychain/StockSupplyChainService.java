package com.finscope.service.supplychain;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.supplychain.StockSupplyChainRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 股票产业链快照读取与异步刷新编排。 */
@Service
public class StockSupplyChainService {
    private static final long RUN_LEASE_MINUTES = 30L;

    private final StrategyInstrumentResolver instrumentResolver;
    private final StockSupplyChainRepository repository;
    private final StockSupplyChainRefreshExecutor refreshExecutor;

    public StockSupplyChainService(StrategyInstrumentResolver instrumentResolver,
                                   StockSupplyChainRepository repository,
                                   StockSupplyChainRefreshExecutor refreshExecutor) {
        this.instrumentResolver = instrumentResolver;
        this.repository = repository;
        this.refreshExecutor = refreshExecutor;
    }

    public StockSupplyChainView get(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        return new StockSupplyChainView(instrument.getCode(), instrument.getName(),
                repository.findSnapshot(instrument.getId()).orElse(null),
                repository.latestRun(instrument.getId()).orElse(null));
    }

    public StockSupplyChainRefreshRun refresh(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        StockSupplyChainRefreshRun active = repository.activeRun(instrument.getId()).orElse(null);
        if (active != null) {
            if (!isStale(active)) {
                throw new BusinessConflictException("该股票产业链仍在刷新中");
            }
            expire(active);
        }
        StockSupplyChainRefreshRun run;
        try {
            run = repository.createRun(instrument.getId());
        } catch (DataAccessException error) {
            if (repository.activeRun(instrument.getId()).isPresent()) {
                throw new BusinessConflictException("该股票产业链仍在刷新中");
            }
            throw error;
        }
        try {
            refreshExecutor.schedule(instrument, run);
        } catch (RuntimeException error) {
            run.setStatus("FAILED");
            run.setStage("COMPLETED");
            run.setErrorCode("QUEUE_REJECTED");
            run.setErrorMessage(error.getClass().getSimpleName());
            run.setMessage("产业链刷新队列暂时繁忙，已保留原快照，可以稍后重试");
            run.setRetryable(true);
            repository.updateRun(run);
        }
        return run;
    }

    private boolean isStale(StockSupplyChainRefreshRun run) {
        return run.getCreatedAt() != null
                && run.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(RUN_LEASE_MINUTES));
    }

    private void expire(StockSupplyChainRefreshRun run) {
        run.setStatus("FAILED");
        run.setStage("COMPLETED");
        run.setErrorCode("STALE_RUN_EXPIRED");
        run.setErrorMessage("InterruptedRefresh");
        run.setMessage("上一次产业链刷新因服务中断未完成，已允许重新刷新");
        run.setRetryable(true);
        repository.updateRun(run);
    }

    public static final class StockSupplyChainView {
        private final String code;
        private final String name;
        private final StockSupplyChainSnapshot snapshot;
        private final StockSupplyChainRefreshRun refreshRun;

        public StockSupplyChainView(String code, String name,
                                    StockSupplyChainSnapshot snapshot,
                                    StockSupplyChainRefreshRun refreshRun) {
            this.code = code;
            this.name = name;
            this.snapshot = snapshot;
            this.refreshRun = refreshRun;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public StockSupplyChainSnapshot getSnapshot() { return snapshot; }
        public StockSupplyChainRefreshRun getRefreshRun() { return refreshRun; }
    }
}
