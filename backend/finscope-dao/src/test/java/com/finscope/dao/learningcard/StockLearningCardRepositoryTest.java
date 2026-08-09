package com.finscope.dao.learningcard;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.learningcard.StockLearningCardWatchItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockLearningCardRepositoryTest {
    private StockLearningCardRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-learning-card-test");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        jdbc.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "600519", "STOCK", "贵州茅台", "2026-08-09T00:00:00", "2026-08-09T00:00:00");
        repository = new StockLearningCardRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
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
