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
import java.time.LocalDateTime;
import java.util.List;

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

    @Test
    void settlesPendingRunOnceAndPersistsTheImmutableOutcome() {
        SingleStockForecastRun saved = repository.save(run("{\"outcome\":true}"));
        SingleStockForecastRun.ForecastOutcome outcome = new SingleStockForecastRun.ForecastOutcome();
        outcome.setEntryDate(LocalDate.of(2026, 8, 10));
        outcome.setExitDate(LocalDate.of(2026, 8, 17));
        outcome.setEntryOpen(10d);
        outcome.setExitOpen(11d);
        outcome.setActualNetReturn(0.0985d);
        outcome.setActualDirection("UP");
        outcome.setCorrect(true);
        outcome.setSettledAt(LocalDateTime.of(2026, 8, 17, 18, 0));
        outcome.setSourceCode("PYTDX");
        outcome.setNote("按冻结 T+1 口径结算");

        assertTrue(repository.settle(saved.getId(), outcome));
        assertTrue(!repository.settle(saved.getId(), outcome));

        SingleStockForecastRun settled = repository.findById(saved.getId()).orElseThrow(AssertionError::new);
        assertEquals(SingleStockForecastRun.MaturityStatus.MATURED, settled.getMaturityStatus());
        assertEquals(0.0985d, settled.getOutcome().getActualNetReturn(), 0.000001d);
        assertEquals(LocalDate.of(2026, 8, 17), settled.getOutcome().getExitDate());
        assertEquals(Boolean.TRUE, settled.getOutcome().getCorrect());
    }

    @Test
    void findsOnlyPendingRunsAndExcludesRepeatedDataFromHealthEvidence() {
        SingleStockForecastRun first = repository.save(run("{\"sequence\":1}"));
        repository.save(run("{\"sequence\":2}"));

        List<SingleStockForecastRun> pending = repository.findPending("603618.SH", 50);
        List<SingleStockForecastRun> evidence = repository.findHealthEvidence(
                "603618.SH", 5, "logistic-walk-forward-v2", 50);

        assertEquals(2, pending.size());
        assertEquals(0, evidence.size());
        assertTrue(repository.markUnavailable(first.getId(), "历史信号日无法定位"));
        assertEquals(SingleStockForecastRun.MaturityStatus.UNAVAILABLE,
                repository.findById(first.getId()).orElseThrow(AssertionError::new).getMaturityStatus());
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
