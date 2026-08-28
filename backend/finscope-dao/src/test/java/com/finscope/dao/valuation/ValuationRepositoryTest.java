package com.finscope.dao.valuation;

import com.finscope.domain.valuation.StockCorporateAction;
import com.finscope.domain.valuation.StockValuationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValuationRepositoryTest {
    @TempDir
    Path tempDir;

    private ValuationRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("valuation.db") + "?foreign_keys=on");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,type TEXT,name TEXT)");
        jdbc.update("INSERT INTO instrument VALUES(7,'600519','STOCK','贵州茅台')");
        ValuationSchemaMigrator migrator = new ValuationSchemaMigrator(
                jdbc, new DataSourceTransactionManager(dataSource));
        migrator.migrate();
        migrator.migrate();
        repository = new ValuationRepository(jdbc);
    }

    @Test
    void snapshotUpsertKeepsOneObservationPerSourceAndDate() {
        repository.saveSnapshot(snapshot("21.30"));
        repository.saveSnapshot(snapshot("20.80"));

        List<StockValuationSnapshot> history = repository.findHistory(
                7L, LocalDate.of(2021, 1, 1));

        assertEquals(1, history.size());
        assertEquals(new BigDecimal("20.80"), history.get(0).getPeTtm());
    }

    @Test
    void corporateActionsRoundTripAsTypedEvents() {
        StockCorporateAction action = new StockCorporateAction();
        action.setInstrumentId(7L);
        action.setExDate(LocalDate.of(2026, 6, 20));
        action.setEventTypes(List.of("CASH_DIVIDEND", "STOCK_DIVIDEND"));
        action.setDividendPerShare(new BigDecimal("23.957"));
        action.setPerShareBonus(new BigDecimal("0.1"));
        action.setCurrency("CNY");
        action.setSourceCode("FUYAO");

        repository.saveCorporateActions(List.of(action));

        assertEquals(List.of("CASH_DIVIDEND", "STOCK_DIVIDEND"),
                repository.findCorporateActions(7L, 10).get(0).getEventTypes());
    }

    private static StockValuationSnapshot snapshot(String pe) {
        StockValuationSnapshot value = new StockValuationSnapshot();
        value.setInstrumentId(7L);
        value.setObservedDate(LocalDate.of(2026, 8, 29));
        value.setObservedAt(Instant.parse("2026-08-29T02:29:58Z"));
        value.setName("贵州茅台");
        value.setPeTtm(new BigDecimal(pe));
        value.setPbMrq(new BigDecimal("7.10"));
        value.setSourceCode("FUYAO");
        value.setQualityStatus("FRESH_PRIMARY");
        return value;
    }
}
