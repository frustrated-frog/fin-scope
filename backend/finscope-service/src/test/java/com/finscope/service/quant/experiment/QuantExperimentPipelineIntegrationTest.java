package com.finscope.service.quant.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.data.QuantLearningDatasetFactory;
import com.finscope.service.quant.strategy.QuantStrategyService;
import com.finscope.service.quant.factor.FactorRegistry;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuantExperimentPipelineIntegrationTest {
    @Test
    void persistsAReproducibleLearningExperimentFromDatasetToEquityCurve() throws Exception {
        JdbcTemplate jdbc = database();
        QuantDatasetRepository datasets = repository(new QuantDatasetRepository(), jdbc);
        QuantMarketDataRepository marketData = repository(new QuantMarketDataRepository(), jdbc);
        QuantStrategyRepository strategies = repository(new QuantStrategyRepository(), jdbc);
        QuantExperimentRepository experiments = repository(new QuantExperimentRepository(), jdbc);

        QuantDatasetService datasetService = new QuantDatasetService();
        ReflectionTestUtils.setField(datasetService, "datasets", datasets);
        ReflectionTestUtils.setField(datasetService, "marketData", marketData);
        ReflectionTestUtils.setField(datasetService, "learningDatasetFactory", new QuantLearningDatasetFactory());
        ReflectionTestUtils.setField(datasetService, "factors", new FactorRegistry());
        QuantDataset dataset = datasetService.createLearningSample("流水线学习样本");
        org.junit.jupiter.api.Assertions.assertTrue(datasetService.availableFactorCodes(dataset.getId()).contains("ROE"));

        QuantStrategyVersion version = new QuantStrategyVersion();
        version.setName("20日动量学习策略"); version.setDatasetId(dataset.getId()); version.setVersion(1);
        version.setSpecJson(new ObjectMapper().writeValueAsString(spec(dataset.getId())));
        version.setStrategyFingerprint("strategy-fingerprint"); version.setDatasetFingerprint(dataset.getFingerprint());
        version.setEngineVersion(QuantStrategyService.ENGINE_VERSION); version.setSource("TEST");
        version = strategies.saveVersion(version);

        QuantExperiment experiment = new QuantExperiment(); experiment.setStrategyVersionId(version.getId());
        experiment.setRequestFingerprint("request-fingerprint"); experiment.setDatasetFingerprint(dataset.getFingerprint());
        experiment.setEngineVersion(QuantStrategyService.ENGINE_VERSION); experiment.setStatus("QUEUED");
        experiment = experiments.save(experiment);

        QuantStrategyService strategyService = new QuantStrategyService();
        ReflectionTestUtils.setField(strategyService, "repository", strategies);
        QuantExperimentRunner runner = new QuantExperimentRunner();
        ReflectionTestUtils.setField(runner, "repository", experiments);
        ReflectionTestUtils.setField(runner, "strategies", strategyService);
        ReflectionTestUtils.setField(runner, "datasets", datasetService);
        ReflectionTestUtils.setField(runner, "marketData", marketData);
        runner.run(experiment.getId());

        QuantExperiment completed = experiments.findById(experiment.getId()).orElseThrow(AssertionError::new);
        assertEquals("SUCCEEDED", completed.getStatus());
        assertNotNull(completed.getResult());
        assertFalse(completed.getResult().getEquityCurve().isEmpty());
        assertFalse(completed.getResult().getTrades().isEmpty());
        assertFalse(completed.getResult().getPositions().isEmpty());
        assertFalse(completed.getResult().getAnnualPerformance().isEmpty());
        assertEquals(9600, marketData.findBars(dataset.getId()).size());
    }

    private JdbcTemplate database() throws Exception {
        Path root = Files.createTempDirectory("finscope-quant-pipeline-test");
        SQLiteDataSource dataSource = new SQLiteDataSource(); dataSource.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource); DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc); ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet(); return jdbc;
    }

    private <T> T repository(T value, JdbcTemplate jdbc) {
        ReflectionTestUtils.setField(value, "jdbcTemplate", jdbc); return value;
    }

    private QuantStrategySpec spec(Long datasetId) {
        QuantStrategySpec value = new QuantStrategySpec(); value.setName("20日动量学习策略"); value.setDatasetId(datasetId);
        value.setBenchmark("EQUAL_WEIGHT"); value.setInvestmentHypothesis("中期趋势具有延续性"); value.setRiskBoundary("仅用于验证流程");
        value.setFactors(Arrays.asList(new QuantStrategySpec.FactorWeight("MOMENTUM_20D", 1d, "HIGH")));
        QuantStrategySpec.Portfolio portfolio = new QuantStrategySpec.Portfolio(); portfolio.setTopN(5); portfolio.setRebalanceEvery(20); portfolio.setWeighting("EQUAL"); value.setPortfolio(portfolio);
        QuantStrategySpec.Filters filters = new QuantStrategySpec.Filters(); filters.setExcludeSt(true); filters.setMinTradingDays(20); filters.setMinAmount(0); value.setFilters(filters);
        QuantStrategySpec.Execution execution = new QuantStrategySpec.Execution(); execution.setSignalPrice("CLOSE"); execution.setFillPrice("NEXT_OPEN"); execution.setSlippageBps(5); value.setExecution(execution);
        QuantStrategySpec.Cost cost = new QuantStrategySpec.Cost(); cost.setBuyCommission(.0003); cost.setSellCommission(.0003); cost.setStampDuty(.0005); cost.setMinimumCommission(5); value.setCost(cost);
        return value;
    }
}
