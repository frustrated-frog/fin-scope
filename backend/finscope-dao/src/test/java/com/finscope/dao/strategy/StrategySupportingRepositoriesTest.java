package com.finscope.dao.strategy;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;
import com.finscope.domain.strategy.StrategyReview;
import com.finscope.domain.strategy.StrategyStockThesis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        StrategyPlaybook playbook = playbook("AUTHOR_QUALITY_TREND");
        StrategyPlaybookRule trend = rule("TREND", "趋势过滤", "FILTER", "只观察周线上升趋势", 1);
        StrategyPlaybookRule risk = rule("RISK", "风险纪律", "CAUTION", "不在下跌趋势中补仓", 2);

        StrategyPlaybook saved = playbooks.save(playbook, Arrays.asList(trend, risk));

        assertEquals("作者经验", playbooks.findByCode(saved.getCode()).orElseThrow(AssertionError::new).getAuthor());
        assertEquals(2, playbooks.findRules(saved.getId()).size());
        assertEquals("TREND", playbooks.findRules(saved.getId()).get(0).getSectionCode());
        assertTrue(playbooks.updateStatus(saved.getCode(), "ACTIVE", "开始执行", saved.getRevision()));
        assertFalse(playbooks.updateStatus(saved.getCode(), "PAUSED", "陈旧写入", saved.getRevision()));
        assertEquals("ACTIVE", playbooks.findByCode(saved.getCode()).orElseThrow(AssertionError::new).getStatus());
        assertThrows(DuplicateKeyException.class, () -> playbooks.save(playbook, Arrays.asList(trend)));
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

    private StrategyPlaybook playbook(String code) {
        StrategyPlaybook value = new StrategyPlaybook();
        value.setCode(code);
        value.setTitle("质量趋势策略");
        value.setScope("A股中长线");
        value.setSummary("质量初筛后确认趋势");
        value.setCadence("每周检查");
        value.setRiskBoundary("不抄底");
        value.setAuthor("作者经验");
        value.setSourceTitle("测试书籍");
        value.setSourceType("BOOK");
        value.setSourceRef("local://test.pdf");
        value.setSourcePublishedAt("2020-03");
        value.setValidationStatus("UNVALIDATED");
        value.setStatus("RESEARCHING");
        return value;
    }

    private StrategyPlaybookRule rule(String sectionCode, String sectionTitle, String ruleType,
                                      String text, int sortOrder) {
        StrategyPlaybookRule value = new StrategyPlaybookRule();
        value.setSectionCode(sectionCode);
        value.setSectionTitle(sectionTitle);
        value.setRuleType(ruleType);
        value.setRuleText(text);
        value.setTestability("QUALITATIVE");
        value.setSourcePage(10 + sortOrder);
        value.setSortOrder(sortOrder);
        return value;
    }
}
