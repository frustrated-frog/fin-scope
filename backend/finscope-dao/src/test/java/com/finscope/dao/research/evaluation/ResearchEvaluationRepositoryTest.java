package com.finscope.dao.research.evaluation;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.evaluation.ResearchEvaluationMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEvaluationRepositoryTest {
    @TempDir
    Path tempDir;
    private ResearchEvaluationRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO research_run(id,run_date,theme_codes,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                9L, "2026-07-26", "china_macro", "COMPLETED", now, now);
        repository = new ResearchEvaluationRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void savesMetricsAndReturnsExistingEvaluationForSameInput() {
        ResearchEvaluation first = repository.save(evaluation("fingerprint-a"));
        ResearchEvaluation second = repository.save(evaluation("fingerprint-a"));

        assertEquals(first.getId(), second.getId());
        assertEquals(1, repository.findAllByRunId(9L).size());
        assertEquals(2, repository.findLatestByRunId(9L).get().getMetrics().size());
        assertTrue(repository.findByIdentity(9L, "deep-research-rules-v1", "fingerprint-a").isPresent());
    }

    private ResearchEvaluation evaluation(String fingerprint) {
        ResearchEvaluation evaluation = new ResearchEvaluation();
        evaluation.setResearchRunId(9L);
        evaluation.setEvaluatorVersion("deep-research-rules-v1");
        evaluation.setInputFingerprint(fingerprint);
        evaluation.setScore(90);
        evaluation.setGateStatus("PASS");
        evaluation.setSummary("score=90");
        evaluation.setCriticalIssues(Arrays.asList("warning-a"));
        evaluation.setMetrics(Arrays.asList(metric("evidence", 23), metric("trace", 20)));
        return evaluation;
    }

    private ResearchEvaluationMetric metric(String code, int score) {
        ResearchEvaluationMetric metric = new ResearchEvaluationMetric();
        metric.setMetricCode(code);
        metric.setLabel(code);
        metric.setScore(score);
        metric.setMaxScore(25);
        metric.setStatus("PASS");
        metric.setEvidence("evidence");
        metric.setRecommendation("recommendation");
        return metric;
    }
}
