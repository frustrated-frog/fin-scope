package com.finscope.dao.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialAnalysisSnapshot;
import com.finscope.domain.financials.FinancialInterpretation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinancialInterpretationPersistenceTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private FinancialAnalysisSnapshotRepository snapshots;
    private FinancialInterpretationRepository interpretations;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("interpretation.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,type TEXT,name TEXT)");
        jdbc.update("INSERT INTO instrument VALUES(7,'600519','STOCK','贵州茅台')");
        FinancialSchemaMigrator migrator = new FinancialSchemaMigrator(
                jdbc, new DataSourceTransactionManager(dataSource));
        migrator.migrate();
        migrator.migrate();
        jdbc.update("INSERT INTO financial_report(instrument_id,period_end,report_type,scope,currency," +
                        "quality_status,source_code,created_at,updated_at) VALUES(7,'2025-12-31','ANNUAL'," +
                        "'CONSOLIDATED','CNY','FRESH','TEST','2026-01-01','2026-01-01')");
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        snapshots = new FinancialAnalysisSnapshotRepository(jdbc);
        interpretations = new FinancialInterpretationRepository(jdbc, json);
    }

    @Test
    void migrationAndSnapshotSaveAreIdempotent() {
        FinancialAnalysisSnapshot first = snapshot();
        FinancialAnalysisSnapshot second = snapshot();

        snapshots.saveOrReuse(first);
        snapshots.saveOrReuse(second);

        assertEquals(first.getId(), second.getId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=302", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_analysis_snapshot", Integer.class));
    }

    @Test
    void interpretationHistoryIsAppendOnlyAndLatestDisplayableSurvivesFailure() {
        FinancialAnalysisSnapshot snapshot = snapshots.saveOrReuse(snapshot());
        FinancialInterpretation success = interpretation(snapshot.getId(), "SUCCESS", "第一次成功");
        FinancialInterpretation failed = interpretation(snapshot.getId(), "FAILED", null);

        interpretations.save(success);
        interpretations.save(failed);

        List<FinancialInterpretation> history = interpretations.findHistory(1L, 20);
        assertEquals(2, history.size());
        assertEquals(failed.getId(), history.get(0).getId());
        assertEquals(success.getId(), interpretations.findLatestDisplayable(1L)
                .orElseThrow(AssertionError::new).getId());
        assertEquals(success.getId(), interpretations.findReusable("generation-key")
                .orElseThrow(AssertionError::new).getId());
        assertNotNull(success.getCreatedAt());
    }

    private FinancialAnalysisSnapshot snapshot() {
        FinancialAnalysisSnapshot value = new FinancialAnalysisSnapshot();
        value.setReportId(1L);
        value.setAlgorithmVersion("financial-analysis-v2");
        value.setSourceHash("source-hash");
        value.setInputHash("input-hash");
        value.setPayloadJson("{\"evidence\":[]}");
        value.setQualityLevel("HIGH");
        return value;
    }

    private FinancialInterpretation interpretation(Long snapshotId, String status, String summary) {
        FinancialInterpretation value = new FinancialInterpretation();
        value.setReportId(1L);
        value.setSnapshotId(snapshotId);
        value.setGenerationKey("generation-key");
        value.setPromptVersion("financial-interpret-v1");
        value.setModelName("test-model");
        value.setStatus(status);
        value.setGenerationMode("SUCCESS".equals(status) ? "LLM" : null);
        value.setResult(summary == null ? null : FinancialInterpretation.Result.fallback(summary));
        value.setFailureCode("FAILED".equals(status) ? "TEST_FAILURE" : null);
        return value;
    }
}
