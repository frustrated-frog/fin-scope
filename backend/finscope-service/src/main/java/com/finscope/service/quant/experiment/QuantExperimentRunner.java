package com.finscope.service.quant.experiment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.backtest.BacktestRequest;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.backtest.QuantBacktestEngine;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.strategy.QuantStrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
@Slf4j
public class QuantExperimentRunner {
    @Resource private QuantExperimentRepository repository;
    @Resource private QuantStrategyService strategies;
    @Resource private QuantDatasetService datasets;
    @Resource private QuantMarketDataRepository marketData;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public void run(Long experimentId) {
        long started = System.currentTimeMillis();
        try {
            QuantExperiment experiment = repository.findById(experimentId).orElseThrow(() -> new IllegalStateException("实验不存在"));
            if (!repository.markRunning(experimentId)) return;
            QuantStrategyVersion version = strategies.getVersion(experiment.getStrategyVersionId());
            QuantDataset dataset = datasets.get(version.getDatasetId());
            if (!version.getDatasetFingerprint().equals(dataset.getFingerprint())) throw new IllegalStateException("数据指纹已变化，请创建新的策略版本");
            QuantStrategySpec spec = mapper.readValue(version.getSpecJson(), QuantStrategySpec.class);
            BacktestRequest request = new BacktestRequest(); request.setSpec(spec); request.setBars(marketData.findBars(dataset.getId()));
            request.setFundamentals(marketData.findFundamentals(dataset.getId()));
            request.setUniverse(marketData.findUniverseMembers(dataset.getId()));
            BacktestResult result = new QuantBacktestEngine().run(request);
            persistSuccess(experimentId, result);
            log.info("量化实验完成 experimentId={} durationMs={} trades={}", experimentId, System.currentTimeMillis() - started, result.getTrades().size());
        } catch (Exception ex) {
            repository.markFailed(experimentId, safe(ex.getMessage()));
            log.warn("量化实验失败 experimentId={} durationMs={} message={}", experimentId, System.currentTimeMillis() - started, ex.getMessage());
        }
    }
    @Transactional
    public void persistSuccess(Long experimentId, BacktestResult result) { repository.complete(experimentId, result); }
    private String safe(String value) { if (value == null) return "量化实验执行失败"; return value.length() > 500 ? value.substring(0, 500) : value; }
}
