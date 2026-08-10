package com.finscope.dao.quant;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleStockForecastRunRepositoryTest {
    private SingleStockForecastRunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-single-forecast-run-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        repository = new SingleStockForecastRunRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void keepsEveryRunAndMarksRepeatedDataWithoutChangingThePayload() {
        SingleStockForecastRun first = repository.save(run("{\"sequence\":1}"));
        SingleStockForecastRun second = repository.save(run("{\"sequence\":2}"));

        assertNotEquals(first.getId(), second.getId());
        assertTrue(second.isSameDataAsPrevious());
        assertEquals("{\"sequence\":2}", repository.findById(second.getId())
                .orElseThrow(AssertionError::new).getReportJson());
        assertEquals(second.getId(), repository.findAll("603618.SH", 20).get(0).getId());
        assertEquals(2, repository.findAll("603618.SH", 20).size());
    }

    @Test
    void isolatesRepeatedDataAndHistoryByForecastHorizon() {
        SingleStockForecastRun fiveDay = run("{\"horizon\":5}");
        fiveDay.setHorizonDays(5);
        repository.save(fiveDay);
        SingleStockForecastRun twentyDay = run("{\"horizon\":20}");
        twentyDay.setHorizonDays(20);

        SingleStockForecastRun saved = repository.save(twentyDay);

        assertTrue(!saved.isSameDataAsPrevious());
        assertEquals(1, repository.findAll("603618.SH", 20, 5).size());
        assertEquals(SingleStockForecastRun.MaturityStatus.PENDING, saved.getMaturityStatus());
    }

    private SingleStockForecastRun run(String reportJson) {
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setInstrumentCode("603618.SH");
        value.setAsOfDate(LocalDate.of(2026, 8, 7));
        value.setHorizonDays(5);
        value.setMaturityStatus(SingleStockForecastRun.MaturityStatus.PENDING);
        value.setStatus("NO_CLEAR_EDGE");
        value.setUpProbability(0.61);
        value.setDataFingerprint("same-fingerprint");
        value.setModelVersion("logistic-walk-forward-v2");
        value.setReportSchemaVersion("single-stock-research-v2");
        value.setReportJson(reportJson);
        value.setHoldingSnapshotJson("{\"held\":false}");
        return value;
    }
}
