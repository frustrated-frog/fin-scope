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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        repository.markRunning(first.getId());
        StockDiscoveryReport report = report();
        repository.complete(first.getId(), report);

        assertEquals(first.getId(), duplicate.getId());
        assertTrue(repository.findLatestSuccess().isPresent());
        assertEquals("SUCCEEDED", repository.findLatestSuccess().get().getStatus());
        JdbcTemplate jdbc = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbcTemplate");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_discovery_sector", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_discovery_candidate", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT final_rank FROM stock_discovery_candidate WHERE instrument_code='600001.SH'",
                Integer.class));
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
        report.setCandidates(List.of(Map.ofEntries(
                Map.entry("code", "600001"), Map.entry("market", "SH"), Map.entry("name", "样本股份"),
                Map.entry("price", 12.34), Map.entry("lot_cost", 1239d), Map.entry("admitted", true),
                Map.entry("rejection_reasons", List.of()), Map.entry("sector_codes", List.of("BK001")),
                Map.entry("sector_names", List.of("人工智能")), Map.entry("lightweight_score", 0.72),
                Map.entry("lightweight_rank", 1))));
        report.setDeepEvidence(List.of(Map.of(
                "code", "600001", "deep_score", 0.78, "final_rank", 1,
                "conclusion", "ROBUST", "calibrated_probability", 0.63, "health_status", "HEALTHY")));
        report.setFinalCandidates(report.getDeepEvidence());
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
}
