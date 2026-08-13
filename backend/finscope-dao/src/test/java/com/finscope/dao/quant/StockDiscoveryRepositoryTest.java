package com.finscope.dao.quant;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

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
        repository.complete(first.getId(), "2026-08-14", "EASTMONEY", "FRESH_PRIMARY",
                "fingerprint", 5, 88, 20, 15, 3, "{\"schema_version\":\"1.0.0\"}");

        assertEquals(first.getId(), duplicate.getId());
        assertTrue(repository.findLatestSuccess().isPresent());
        assertEquals("SUCCEEDED", repository.findLatestSuccess().get().getStatus());
    }
}
