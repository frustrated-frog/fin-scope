package com.finscope.dao.strategy;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyReview;
import com.finscope.domain.strategy.StrategyStockThesis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategySupportingRepositoriesTest {
    private StrategyPlaybookRepository playbooks;
    private StrategyStockThesisRepository theses;
    private StrategyReviewRepository reviews;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-strategy-support-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        jdbc.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "600519", "STOCK", "贵州茅台", "2026-07-12T00:00:00", "2026-07-12T00:00:00");

        playbooks = new StrategyPlaybookRepository();
        theses = new StrategyStockThesisRepository();
        reviews = new StrategyReviewRepository();
        ReflectionTestUtils.setField(playbooks, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(theses, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(reviews, "jdbcTemplate", jdbc);
    }

    @Test
    void persistsPlaybookStateWithOptimisticLock() {
        StrategyPlaybook playbook = playbooks.upsert("FUND_REBALANCE", "RESEARCHING", "先研究");
        assertTrue(playbooks.updateStatus("FUND_REBALANCE", "ACTIVE", "开始执行", playbook.getRevision()));
        assertFalse(playbooks.updateStatus("FUND_REBALANCE", "PAUSED", "陈旧写入", playbook.getRevision()));
        assertEquals("ACTIVE", playbooks.findByCode("FUND_REBALANCE").orElseThrow(AssertionError::new).getStatus());
    }

    @Test
    void persistsStockThesisAndReview() {
        StrategyStockThesis thesis = new StrategyStockThesis();
        thesis.setInstrumentId(1L);
        thesis.setStage("RESEARCH_POOL");
        thesis.setThesis("高质量消费龙头具备长期复利能力");
        thesis.setBuyConditions("估值进入历史合理区间");
        thesis.setInvalidationConditions("护城河或盈利能力持续恶化");
        thesis.setWatchFocus("现金流与价格带");
        StrategyStockThesis saved = theses.save(thesis);
        assertTrue(theses.updateStage(saved.getId(), "WATCH_POOL", saved.getRevision()));
        assertEquals("贵州茅台", theses.findAllWithInstrument().get(0).getName());

        StrategyReview review = new StrategyReview();
        review.setReviewDate(LocalDate.of(2026, 7, 12));
        review.setFacts("组合尚未建立");
        review.setReasoning("先定义角色再投入");
        review.setNextAction("添加第一只基金");
        reviews.save(review);
        assertEquals("添加第一只基金", reviews.findAll().get(0).getNextAction());
    }
}
