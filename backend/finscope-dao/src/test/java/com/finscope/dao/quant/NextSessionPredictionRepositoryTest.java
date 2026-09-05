package com.finscope.dao.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.dao.config.DatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NextSessionPredictionRepositoryTest {
    @TempDir
    Path root;
    private JdbcTemplate jdbc;
    private NextSessionPredictionRepository repository;

    @BeforeEach
    void setup() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("test.db"));
        jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        repository = new NextSessionPredictionRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "json", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void importsFrozenReportsOnceAndNeverRewritesASettledOutcome() {
        insertDiscovery("2026-09-04T16:00:00", "READY");
        assertEquals(1, repository.importFrozenReports());
        assertEquals(0, repository.importFrozenReports());
        var prediction = repository.findPending(10).get(0);
        assertEquals("2026-09-07", prediction.getPrediction().getTargetDate().toString());
        assertEquals(0.65, prediction.getPrediction().getUpProbability());
        assertTrue(repository.settle(prediction.getId(), 0.02, true, true,
                LocalDateTime.of(2026, 9, 7, 16, 0), "TEST"));
        assertFalse(repository.settle(prediction.getId(), -0.02, false, false,
                LocalDateTime.of(2026, 9, 8, 16, 0), "CHANGED"));
        assertTrue(repository.findPending(10).isEmpty());
        assertEquals(0.02, repository.history("000001", 10).get(0).getActualReturn());
    }

    @Test
    void neverImportsPredictionsGeneratedAfterTheirTargetDayStartedOrStaleResults() {
        insertDiscovery("2026-09-07T16:00:00", "READY");
        assertEquals(0, repository.importFrozenReports());
        jdbc.update("DELETE FROM stock_discovery_run");
        insertDiscovery("2026-09-04T16:00:00", "STALE_DATA");
        assertEquals(0, repository.importFrozenReports());
    }

    @Test
    void importsSingleStockReportsAndDeduplicatesTheSameDiscoveryPrediction() {
        insertDiscovery("2026-09-04T16:00:00", "READY");
        jdbc.update("INSERT INTO single_stock_forecast_run(instrument_code,horizon_days,as_of_date,"
                + "created_at,status,data_fingerprint,model_version,report_schema_version,report_json) "
                + "SELECT '000001.SZ',5,'2026-09-04','2026-09-04T16:00:00','READY','test','v1','v1',"
                + "json_extract(report_json,'$.deep_evidence[0].forecast_report') FROM stock_discovery_run");
        assertEquals(1, repository.importFrozenReports());
        assertEquals(1, repository.history("000001", 10).size());
    }

    private void insertDiscovery(String generatedAt, String status) {
        String prediction = "{\"status\":\"" + status + "\",\"asOfDate\":\"2026-09-04\","
                + "\"targetDate\":\"2026-09-07\",\"generatedAt\":\"" + generatedAt + "\","
                + "\"modelVersion\":\"next-session-v1\",\"dataFingerprint\":\"" + "a".repeat(64) + "\","
                + "\"label\":\"NEXT_CLOSE_RETURN\",\"upProbability\":0.65,\"lowerReturn\":-0.02,"
                + "\"upperReturn\":0.04,\"lastClose\":10,\"decision\":\"UP\"}";
        String report = "{\"deep_evidence\":[{\"code\":\"000001\",\"forecast_report\":{"
                + "\"instrumentCode\":\"000001.SZ\",\"nextSession\":" + prediction + "}}]}";
        jdbc.update("INSERT INTO stock_discovery_run(run_key,business_date,trigger_type,status,budget,"
                + "policy_version,created_at,report_json) VALUES(?,?,'TEST','SUCCEEDED',6000,'TEST',?,?)",
                "test", "2026-09-04", generatedAt, report);
    }
}
