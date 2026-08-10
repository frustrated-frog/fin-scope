package com.finscope.dao.learningcard;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.learningcard.StockLearningCardSection;
import com.finscope.domain.learningcard.StockLearningCardSummary;
import com.finscope.domain.learningcard.StockLearningCardWatchItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockLearningCardRepositoryTest {
    private StockLearningCardRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-learning-card-test");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        jdbc.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "600519", "STOCK", "贵州茅台", "2026-08-09T00:00:00", "2026-08-09T00:00:00");
        repository = new StockLearningCardRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper());
    }

    @Test
    void persistsImmutableSixDimensionLearningRun() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun run = new StockLearningCardRun();
        run.setCardId(card.getId());
        run.setResearchRunId(88L);
        run.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        run.setStatus("READY");
        run.setConclusionStatus("CONTINUE_LEARNING");
        run.setSummary("公开材料支持继续学习");
        run.setEvidenceCompleteness("PARTIAL");
        run.setGenerationMode("CONTROLLED");
        StockLearningCardClaim space = claim("SPACE", "行业空间仍待量化", 1);
        space.setHeadline("增长依赖高端产品放量");
        space.setRatingLabel("成长空间");
        space.setRatingValue("MEDIUM_HIGH");
        StockLearningCardSection driver = new StockLearningCardSection();
        driver.setKey("growth_drivers");
        driver.setTitle("增量引擎");
        driver.setContent("高端产品是主要增量来源 [E1]");
        driver.setEvidenceRefs(Arrays.asList("E1"));
        driver.setVerificationStatus("SUPPORTED");
        driver.setSortOrder(1);
        space.setSections(Arrays.asList(driver));
        StockLearningCardWatchItem watch = new StockLearningCardWatchItem();
        watch.setMetric("高端白酒需求");
        watch.setBaseline("当前公开证据未覆盖");
        watch.setFrequency("下一次财报");
        watch.setUpgradeCondition("量价同步改善");
        watch.setDowngradeCondition("需求连续走弱");
        watch.setSortOrder(1);

        StockLearningCardRun saved = repository.appendRun(run, Arrays.asList(space,
                claim("PROFIT_MODEL", "盈利模式待验证", 2), claim("COMPETITION", "竞争优势待验证", 3),
                claim("GOVERNANCE", "治理待验证", 4), claim("VALUATION", "估值待验证", 5),
                claim("COUNTER_CASE", "反方待验证", 6)), Arrays.asList(watch));

        StockLearningCardRun restored = repository.findRun(saved.getId()).orElseThrow(AssertionError::new);
        assertEquals(6, restored.getClaims().size());
        assertEquals("SPACE", restored.getClaims().get(0).getDimensionCode());
        assertEquals("增长依赖高端产品放量", restored.getClaims().get(0).getHeadline());
        assertEquals("成长空间", restored.getClaims().get(0).getRatingLabel());
        assertEquals("MEDIUM_HIGH", restored.getClaims().get(0).getRatingValue());
        assertEquals(1, restored.getClaims().get(0).getSections().size());
        assertEquals("growth_drivers", restored.getClaims().get(0).getSections().get(0).getKey());
        assertEquals(Arrays.asList("E1"), restored.getClaims().get(0).getSections().get(0).getEvidenceRefs());
        assertEquals("SUPPORTED", restored.getClaims().get(0).getSections().get(0).getVerificationStatus());
        assertEquals("高端白酒需求", restored.getWatchItems().get(0).getMetric());
        assertEquals(saved.getId(), repository.latest(card.getId()).orElseThrow(AssertionError::new).getId());
        assertThrows(Exception.class, () -> repository.appendRun(run, Arrays.asList(claim("SPACE", "重复", 1), claim("SPACE", "重复", 2)), Arrays.asList()));
    }

    @Test
    void keepsTheCompletionTimeEmptyWhileResearchIsRunning() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun run = new StockLearningCardRun();
        run.setCardId(card.getId()); run.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        run.setStatus("RUNNING"); run.setEvidenceCompleteness("PENDING"); run.setGenerationMode("CONTROLLED");

        StockLearningCardRun saved = repository.appendRun(run, Arrays.<StockLearningCardClaim>asList(), Arrays.<StockLearningCardWatchItem>asList());

        assertNull(saved.getCompletedAt());
    }

    @Test
    void persistsIndependentAgentProgressAndDimensionFailure() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun run = new StockLearningCardRun();
        run.setCardId(card.getId());
        run.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        run.setStatus("DEGRADED");
        run.setStage("COMPLETED");
        run.setFailedStage("SYNTHESIZING_CARDS");
        run.setErrorCode("DIMENSION_PARTIAL_FAILURE");
        run.setUserMessage("竞争格局生成失败，其他学习卡已保留");
        run.setRetryable(true);
        run.setEvidenceCompleteness("PARTIAL");
        run.setGenerationMode("MODEL_ASSISTED");
        StockLearningCardClaim failed = claim("COMPETITION", "暂未形成判断", 3);
        failed.setStatus("FAILED");
        failed.setFailureMessage("该维度生成失败，可以重新生成学习卡");

        StockLearningCardRun saved = repository.appendRun(run, Arrays.asList(failed), Arrays.asList());
        StockLearningCardRun restored = repository.findRun(saved.getId()).orElseThrow(AssertionError::new);

        assertEquals("COMPLETED", restored.getStage());
        assertEquals("SYNTHESIZING_CARDS", restored.getFailedStage());
        assertEquals("DIMENSION_PARTIAL_FAILURE", restored.getErrorCode());
        assertEquals("竞争格局生成失败，其他学习卡已保留", restored.getUserMessage());
        assertEquals(true, restored.isRetryable());
        assertEquals("FAILED", restored.getClaims().get(0).getStatus());
        assertEquals("该维度生成失败，可以重新生成学习卡", restored.getClaims().get(0).getFailureMessage());
    }

    @Test
    void updatesAnAsynchronousRunWithoutCreatingAnotherVersion() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun queued = new StockLearningCardRun();
        queued.setCardId(card.getId()); queued.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        queued.setStatus("RUNNING"); queued.setStage("QUEUED"); queued.setEvidenceCompleteness("PENDING");
        queued.setGenerationMode("CONTROLLED");
        StockLearningCardRun saved = repository.appendRun(queued, Arrays.asList(), Arrays.asList());
        saved.setStatus("READY"); saved.setStage("COMPLETED"); saved.setEvidenceCompleteness("COMPLETE");
        saved.setGenerationMode("MODEL_ASSISTED");

        repository.updateRun(saved, Arrays.asList(claim("SPACE", "空间判断", 1)),
                Arrays.<StockLearningCardWatchItem>asList());

        StockLearningCardRun restored = repository.latest(card.getId()).orElseThrow(AssertionError::new);
        assertEquals(saved.getId(), restored.getId());
        assertEquals("READY", restored.getStatus());
        assertEquals("COMPLETED", restored.getStage());
        assertEquals(1, restored.getClaims().size());
    }

    @Test
    void allowsOnlyOneRunningVersionPerStockCard() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun first = new StockLearningCardRun();
        first.setCardId(card.getId()); first.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        first.setStatus("RUNNING"); first.setStage("QUEUED"); first.setGenerationMode("CONTROLLED");
        repository.appendRun(first, Arrays.asList(), Arrays.asList());
        StockLearningCardRun second = new StockLearningCardRun();
        second.setCardId(card.getId()); second.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        second.setStatus("RUNNING"); second.setStage("QUEUED"); second.setGenerationMode("CONTROLLED");

        assertThrows(Exception.class, () -> repository.appendRun(second, Arrays.asList(), Arrays.asList()));
    }

    @Test
    void persistsTraceableEvidenceForEachLearningDimension() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun run = new StockLearningCardRun();
        run.setCardId(card.getId()); run.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        run.setStatus("READY"); run.setGenerationMode("MODEL_ASSISTED");
        StockLearningCardRun saved = repository.appendRun(run, Arrays.asList(), Arrays.asList());
        StockLearningCardEvidence evidence = new StockLearningCardEvidence("E1", "年度报告", "https://example.com/report",
                "example.com", "2026-03-31", "公开资料正文");
        evidence.setDimensionCode("SPACE"); evidence.setContentOrigin("FULL_TEXT"); evidence.setSortOrder(1);

        repository.replaceEvidence(saved.getId(), Arrays.asList(evidence));

        StockLearningCardEvidence restored = repository.findRun(saved.getId()).orElseThrow(AssertionError::new).getEvidence().get(0);
        assertEquals("SPACE", restored.getDimensionCode());
        assertEquals("E1", restored.getEvidenceCode());
        assertEquals("https://example.com/report", restored.getUrl());
        assertEquals("FULL_TEXT", restored.getContentOrigin());
    }

    @Test
    void findsAnOlderActiveRunEvenWhenANewerTerminalRunExists() {
        StockLearningCard card = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun active = new StockLearningCardRun();
        active.setCardId(card.getId()); active.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        active.setStatus("RUNNING"); active.setStage("COLLECTING_EVIDENCE"); active.setGenerationMode("CONTROLLED");
        StockLearningCardRun savedActive = repository.appendRun(active, Arrays.asList(), Arrays.asList());
        StockLearningCardRun terminal = new StockLearningCardRun();
        terminal.setCardId(card.getId()); terminal.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        terminal.setStatus("DEGRADED"); terminal.setStage("COMPLETED"); terminal.setGenerationMode("CONTROLLED");
        repository.appendRun(terminal, Arrays.asList(), Arrays.asList());

        StockLearningCardRun restored = repository.active(card.getId()).orElseThrow(AssertionError::new);

        assertEquals(savedActive.getId(), restored.getId());
        assertEquals("RUNNING", restored.getStatus());
    }

    @Test
    void listsOneLatestSummaryPerStockWithCompletedDimensionCount() {
        StockLearningCard first = repository.findOrCreate(1L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun firstRun = new StockLearningCardRun();
        firstRun.setCardId(first.getId()); firstRun.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        firstRun.setStatus("DEGRADED"); firstRun.setStage("COMPLETED"); firstRun.setSummary("已生成两个维度");
        firstRun.setGenerationMode("MODEL_ASSISTED");
        StockLearningCardClaim ready = claim("SPACE", "空间判断", 1); ready.setStatus("READY");
        StockLearningCardClaim secondReady = claim("PROFIT_MODEL", "盈利判断", 2); secondReady.setStatus("READY");
        StockLearningCardClaim failed = claim("COMPETITION", "暂未形成判断", 3); failed.setStatus("FAILED");
        repository.appendRun(firstRun, Arrays.asList(ready, secondReady, failed), Arrays.asList());
        jdbc.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "000001", "STOCK", "平安银行", "2026-08-09T00:00:00", "2026-08-09T00:00:00");
        StockLearningCard second = repository.findOrCreate(2L, "LIUJIE_BUYSIDE_RESEARCH_V1");
        StockLearningCardRun secondRun = new StockLearningCardRun();
        secondRun.setCardId(second.getId()); secondRun.setFrameworkCode("LIUJIE_BUYSIDE_RESEARCH_V1");
        secondRun.setStatus("RUNNING"); secondRun.setStage("COLLECTING_EVIDENCE");
        secondRun.setSummary("正在收集公开资料"); secondRun.setGenerationMode("CONTROLLED");
        repository.appendRun(secondRun, Arrays.asList(), Arrays.asList());

        java.util.List<StockLearningCardSummary> summaries = repository.summaries();

        assertEquals(2, summaries.size());
        assertEquals("000001", summaries.get(0).getCode());
        assertEquals("RUNNING", summaries.get(0).getStatus());
        assertEquals("600519", summaries.get(1).getCode());
        assertEquals(2, summaries.get(1).getCompletedDimensions());
        assertEquals(6, summaries.get(1).getTotalDimensions());
    }

    private StockLearningCardClaim claim(String dimension, String judgment, int order) {
        StockLearningCardClaim value = new StockLearningCardClaim();
        value.setDimensionCode(dimension);
        value.setJudgment(judgment);
        value.setRationale("只依据本次公开研究材料");
        value.setCounterargument("仍需寻找反方材料");
        value.setUnknowns("资料有限");
        value.setConfidence("LOW");
        value.setSortOrder(order);
        return value;
    }
}
