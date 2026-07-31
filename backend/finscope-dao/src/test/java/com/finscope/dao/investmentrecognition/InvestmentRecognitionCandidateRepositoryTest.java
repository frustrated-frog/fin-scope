package com.finscope.dao.investmentrecognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentRecognitionCandidateRepositoryTest {
    private InvestmentRecognitionCandidateRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("investment-recognition-repository");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        repository = new InvestmentRecognitionCandidateRepository(jdbc, new ObjectMapper());
    }

    @Test
    void savesStructuredCandidateAndMovesStatusWithOptimisticLock() {
        InvestmentRecognitionCandidate value = candidate();
        InvestmentRecognitionCandidate saved = repository.saveOrRefresh(value);

        assertTrue(saved.getId() > 0L);
        assertEquals(Arrays.asList("涨跌幅 +3.20%", "成交额 12.00"), saved.getSupportingData());
        assertEquals("CANDIDATE", saved.getStatus());

        assertTrue(repository.updateStatus(saved.getId(), "ACCEPTED", 0L, 9L));
        assertFalse(repository.updateStatus(saved.getId(), "DISMISSED", 0L, null));
        InvestmentRecognitionCandidate accepted = repository.findByStatus("ACCEPTED").get(0);
        assertEquals(9L, accepted.getTopicId());
        assertEquals(1L, accepted.getRevision());
    }

    @Test
    void refreshesTheSameObservationInsteadOfCreatingDuplicates() {
        InvestmentRecognitionCandidate first = repository.saveOrRefresh(candidate());
        InvestmentRecognitionCandidate changed = candidate();
        changed.setThesis("更新后的命题");
        InvestmentRecognitionCandidate second = repository.saveOrRefresh(changed);

        assertEquals(first.getId(), second.getId());
        assertEquals("更新后的命题", second.getThesis());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void neverRefreshesATerminalCandidateBackIntoTheAgentQueue() {
        InvestmentRecognitionCandidate saved = repository.saveOrRefresh(candidate());
        assertTrue(repository.updateStatus(saved.getId(), "ACCEPTED", saved.getRevision(), 9L));
        InvestmentRecognitionCandidate regenerated = candidate();
        regenerated.setThesis("Agent 再次生成的命题");

        InvestmentRecognitionCandidate result = repository.saveOrRefresh(regenerated);

        assertEquals("ACCEPTED", result.getStatus());
        assertEquals("价格变化值得检查盈利预期是否上修", result.getThesis());
        assertEquals(1L, result.getRevision());
    }

    private InvestmentRecognitionCandidate candidate() {
        InvestmentRecognitionCandidate value = new InvestmentRecognitionCandidate();
        value.setFingerprint("STOCK:600519:2026-08-01:+3.20");
        value.setSubjectType("STOCK");
        value.setSubjectCode("600519");
        value.setSubjectName("贵州茅台");
        value.setStatus("CANDIDATE");
        value.setThesis("价格变化值得检查盈利预期是否上修");
        value.setObservedChange("当日上涨 3.20%");
        value.setMechanism("若盈利预期同步上修，估值压力可能被部分消化");
        value.setSupportingData(Arrays.asList("涨跌幅 +3.20%", "成交额 12.00"));
        value.setCounterData(Arrays.asList("单日价格可能受情绪驱动"));
        value.setValidationMetrics(Arrays.asList("后续财报收入增速", "未来五日成交持续性"));
        value.setInvalidationConditions("价格回落且盈利预期未改善");
        value.setHorizon("未来 5 个交易日到下一财报期");
        value.setConfidence("MEDIUM");
        value.setEvidenceCompleteness("SUFFICIENT");
        value.setDataAsOf("2026-08-01T10:00:00");
        return value;
    }
}
