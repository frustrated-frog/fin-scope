package com.finscope.dao.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockDiscoveryRepositoryTest {
    private StockDiscoveryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("stock-discovery-repository");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db") + "?foreign_keys=on");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        repository = new StockDiscoveryRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper());
    }

    @Test
    void createsRunIdempotentlyAndReturnsLatestSuccess() {
        StockDiscoveryRun first = repository.createIfAbsent(
                "2026-08-14:stock-discovery-v1", LocalDate.of(2026, 8, 14), 6000d,
                "stock-discovery-v1", "SCHEDULED");
        StockDiscoveryRun duplicate = repository.createIfAbsent(
                "2026-08-14:stock-discovery-v1", LocalDate.of(2026, 8, 14), 6000d,
                "stock-discovery-v1", "RECOVERY");

        assertTrue(repository.tryMarkRunning(first.getId(), "attempt-a"));
        assertFalse(repository.tryMarkRunning(first.getId(), "attempt-b"));
        StockDiscoveryReport report = report();
        repository.complete(first.getId(), "attempt-a", report);

        assertEquals(first.getId(), duplicate.getId());
        assertTrue(repository.findLatestSuccess().isPresent());
        assertEquals("SUCCEEDED", repository.findLatestSuccess().get().getStatus());
        JdbcTemplate jdbc = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbcTemplate");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_discovery_sector", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_discovery_candidate", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT final_rank FROM stock_discovery_candidate WHERE instrument_code='600001.SH'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_discovery_candidate "
                        + "WHERE instrument_code='000002.SZ' AND lightweight_score IS NULL "
                        + "AND lightweight_rank IS NULL AND deep_score IS NULL "
                        + "AND calibrated_probability IS NULL",
                Integer.class));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT maturity_status FROM stock_discovery_candidate WHERE instrument_code='600001.SH'",
                String.class));
        assertEquals("NOT_APPLICABLE", jdbc.queryForObject(
                "SELECT maturity_status FROM stock_discovery_candidate WHERE instrument_code='000002.SZ'",
                String.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_discovery_model_prediction WHERE instrument_code='600001.SH'",
                Integer.class));
    }

    @Test
    void settlesCandidateAndItsFrozenModelPredictionsIdempotently() {
        StockDiscoveryRun run = repository.createIfAbsent(
                "2026-08-14:stock-discovery-v1", LocalDate.of(2026, 8, 14), 6000d,
                "stock-discovery-v1", "SCHEDULED");
        assertTrue(repository.tryMarkRunning(run.getId(), "attempt-a"));
        repository.complete(run.getId(), "attempt-a", report());

        com.finscope.domain.quant.discovery.StockDiscoveryCandidate candidate =
                repository.findPendingCandidates(10).get(0);
        assertTrue(repository.settleCandidate(candidate, LocalDate.of(2026, 8, 17), 12d,
                LocalDate.of(2026, 8, 24), 12.8d, 0.064d, "UP", true,
                LocalDateTime.of(2026, 8, 24, 18, 0), "PYTHON_QFQ_DAILY"));
        assertFalse(repository.settleCandidate(candidate, LocalDate.of(2026, 8, 17), 12d,
                LocalDate.of(2026, 8, 24), 12.8d, 0.064d, "UP", true,
                LocalDateTime.of(2026, 8, 24, 18, 1), "PYTHON_QFQ_DAILY"));

        JdbcTemplate jdbc = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbcTemplate");
        assertEquals("MATURED", jdbc.queryForObject(
                "SELECT maturity_status FROM stock_discovery_candidate WHERE id=?",
                String.class, candidate.getId()));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_discovery_model_prediction "
                        + "WHERE run_id=? AND maturity_status='MATURED' AND actual_direction='UP'",
                Integer.class, run.getId()));
        assertEquals(1, repository.findMaturedCandidates(10).size());
        assertEquals(2, repository.findMaturedModelPredictions(10).size());
    }

    @Test
    void staleWorkerCannotOverwriteTheNewClaim() {
        StockDiscoveryRun run = repository.createIfAbsent(
                "2026-08-15:stock-discovery-v1", LocalDate.of(2026, 8, 15), 6000d,
                "stock-discovery-v1", "RECOVERY");
        assertTrue(repository.tryMarkRunning(run.getId(), "attempt-old"));
        JdbcTemplate jdbc = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbcTemplate");
        jdbc.update("UPDATE stock_discovery_run SET started_at='2000-01-01 00:00:00' WHERE id=?", run.getId());
        assertTrue(repository.tryMarkRunning(run.getId(), "attempt-new"));

        assertThrows(IllegalStateException.class,
                () -> repository.complete(run.getId(), "attempt-old", report()));
        assertEquals("RUNNING", repository.findById(run.getId()).orElseThrow().getStatus());

        repository.complete(run.getId(), "attempt-new", report());
        assertEquals("SUCCEEDED", repository.findById(run.getId()).orElseThrow().getStatus());
    }

    private StockDiscoveryReport report() {
        StockDiscoveryReport report = new StockDiscoveryReport();
        report.setAsOfDate("2026-08-14");
        report.setSourceFamily("EASTMONEY");
        report.setQualityStatus("FRESH_PRIMARY");
        report.setDataFingerprint("fingerprint");
        report.setSectors(List.of(Map.of(
                "code", "BK001", "name", "人工智能", "category", "CONCEPT", "source_code", "EASTMONEY",
                "source_family", "EASTMONEY", "period", "5D", "source_rank", 1,
                "retrieved_at", "2026-08-14T15:30:00+08:00")));
        report.setCandidates(List.of(
                Map.ofEntries(
                        Map.entry("code", "600001"), Map.entry("market", "SH"),
                        Map.entry("name", "样本股份"), Map.entry("price", 12.34),
                        Map.entry("lot_cost", 1239d), Map.entry("admitted", true),
                        Map.entry("rejection_reasons", List.of()),
                        Map.entry("sector_codes", List.of("BK001")),
                        Map.entry("sector_names", List.of("人工智能")),
                        Map.entry("lightweight_score", 0.72), Map.entry("lightweight_rank", 1)),
                Map.ofEntries(
                        Map.entry("code", "000002"), Map.entry("market", "SZ"),
                        Map.entry("name", "拒绝样本"), Map.entry("price", 80d),
                        Map.entry("lot_cost", 8005d), Map.entry("admitted", false),
                        Map.entry("rejection_reasons", List.of("OVER_BUDGET")),
                        Map.entry("sector_codes", List.of("BK001")),
                        Map.entry("sector_names", List.of("人工智能")))));
        report.setDeepEvidence(List.of(Map.ofEntries(
                Map.entry("code", "600001"),
                Map.entry("conclusion", "ROBUST"),
                Map.entry("calibrated_probability", 0.63),
                Map.entry("health_status", "HEALTHY"),
                Map.entry("forecast_report", Map.of(
                        "modelCompetition", Map.of(
                                "candidates", List.of(
                                        model("LOGISTIC", "逻辑回归", "CHAMPION", 0.63, "UP", "QUALIFIED"),
                                        model("HIST_GRADIENT_BOOSTING", "梯度提升", "CHALLENGER", 0.58,
                                                "UP", "CONDITIONAL"))))))));
        report.setFinalCandidates(List.of(Map.of(
                "code", "600001", "deep_score", 0.78, "final_rank", 1,
                "conclusion", "ROBUST", "calibrated_probability", 0.63, "health_status", "HEALTHY")));
        StockDiscoveryReport.Funnel funnel = new StockDiscoveryReport.Funnel();
        funnel.setConstituentCount(88);
        funnel.setAdmittedCount(20);
        funnel.setQuantifiedCount(20);
        funnel.setDeepReviewCount(15);
        funnel.setFinalCount(1);
        report.setFunnel(funnel);
        report.setRawJson("{\"schema_version\":\"1.0.0\"}");
        return report;
    }

    private Map<String, Object> model(String code, String name, String role, double probability,
                                      String decision, String qualification) {
        return Map.of(
                "code", code,
                "name", name,
                "role", role,
                "modelVersion", "competition-v6",
                "calibratedProbability", probability,
                "shadowDecision", decision,
                "qualificationStatus", qualification);
    }
}
