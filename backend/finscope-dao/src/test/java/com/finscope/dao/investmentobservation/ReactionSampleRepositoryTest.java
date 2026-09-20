package com.finscope.dao.investmentobservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.domain.investmentobservation.ReactionCalculation;
import com.finscope.domain.investmentobservation.ReactionSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReactionSampleRepositoryTest {
    @TempDir
    Path temp;
    private ReactionSampleRepository repository;
    private JdbcTemplate jdbc;
    private final LocalDateTime now = LocalDateTime.parse("2026-09-20T09:00:00");

    @BeforeEach
    void setup() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + temp.resolve("test.db"));
        jdbc = new JdbcTemplate(source);
        ReactionSchemaMigrator migrator = new ReactionSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager", new DataSourceTransactionManager(source));
        migrator.afterPropertiesSet();
        migrator.afterPropertiesSet();
        repository = new ReactionSampleRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void migrationAndRegistrationAreIdempotentAndKeepOriginalSource() {
        ReactionSample first = repository.create(sample());
        ReactionSample duplicate = sample();
        duplicate.setTitle("later source edit");
        assertEquals(first.getId(), repository.create(duplicate).getId());
        assertEquals("contract", repository.findById(first.getId()).orElseThrow().getTitle());
        assertEquals(1, repository.list(null, 0, 100).size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM schema_migration WHERE version=410", Integer.class));
        assertTrue(repository.list(null, first.getId(), 100).isEmpty());
    }

    @Test
    void confirmationAndRefreshUseRevisionAndFailedRefreshKeepsLastSuccess() {
        ReactionSample saved = repository.create(sample());
        saved.setInstrumentCode("600519.SH");
        assertTrue(repository.confirm(saved, 0));
        assertFalse(repository.confirm(saved, 0));
        ReactionCalculation calculation = new ReactionCalculation();
        calculation.setCalculatedAt(now);
        assertTrue(repository.saveCalculation(saved.getId(), 1, calculation, now));
        assertTrue(repository.saveFailure(saved.getId(), 2, "market unavailable", now.plusHours(1)));
        ReactionSample failed = repository.findById(saved.getId()).orElseThrow();
        assertEquals(now, failed.getCalculation().getCalculatedAt());
        assertEquals("market unavailable", failed.getRefreshError());
        assertTrue(repository.changeState(saved.getId(), 3, ReactionSampleState.ARCHIVED));
        assertFalse(repository.saveCalculation(saved.getId(), 4, calculation, now));
        assertTrue(repository.changeState(saved.getId(), 4, ReactionSampleState.OBSERVING));
        assertEquals(1, repository.list(ReactionSampleState.OBSERVING, 0, 10).size());
    }

    @Test
    void endedWindowWithPermanentGapUsesDailyRetryThenStops() {
        ReactionSample sample = sample();
        sample.setState(ReactionSampleState.OBSERVING);
        sample = repository.create(sample);
        ReactionCalculation calculation = new ReactionCalculation();
        var window = new com.finscope.domain.investmentobservation.ReactionWindow();
        window.setSessions(5);
        window.setEndDate(now.toLocalDate().minusDays(1));
        window.setStatus(com.finscope.common.enums.investmentobservation.ReactionWindowStatus.MISSING_DATA);
        calculation.setWindows(java.util.List.of(window));
        assertTrue(repository.saveCalculation(sample.getId(), 0, calculation, now));
        assertTrue(repository.findDue(now.plusHours(2), 20).isEmpty());
        assertEquals(1, repository.findDue(now.plusDays(2), 20).size());
        assertTrue(repository.saveCalculation(sample.getId(), 1, calculation, now.plusDays(7)));
        assertTrue(repository.findDue(now.plusDays(30), 20).isEmpty());
    }

    @Test
    void changesAreIdempotentAndSameDayPriceRevisionIsACorrection() {
        ReactionSample sample = sample();
        sample.setState(ReactionSampleState.OBSERVING);
        sample = repository.create(sample);
        ReactionCalculation calculation = new ReactionCalculation();
        var point = new com.finscope.domain.investmentobservation.ReactionPoint();
        point.setSession(1);
        point.setTradeDate(now.toLocalDate());
        point.setStockReturnPct(java.math.BigDecimal.ONE);
        point.setStatus(com.finscope.common.enums.investmentobservation.ReactionWindowStatus.READY);
        calculation.setPoints(java.util.List.of(point));
        assertTrue(repository.saveCalculation(sample.getId(), 0, calculation, now));
        assertTrue(repository.saveCalculation(sample.getId(), 1, calculation, now.plusMinutes(20)));
        assertEquals(1, repository.changes(now.toLocalDate(), Long.MAX_VALUE, 100).size());
        point.setStockReturnPct(java.math.BigDecimal.TEN);
        assertTrue(repository.saveCalculation(sample.getId(), 2, calculation, now.plusMinutes(40)));
        var changes = repository.changes(now.toLocalDate(), Long.MAX_VALUE, 100);
        assertEquals(2, changes.size());
        assertEquals(com.finscope.common.enums.investmentobservation.ReactionChangeType.DATA_CORRECTION, changes.get(0).getChangeType());
        assertFalse(repository.saveCalculation(sample.getId(), 2, calculation, now));
        assertEquals(2, repository.changes(now.toLocalDate(), Long.MAX_VALUE, 100).size());
        assertTrue(repository.followEvent(sample.getSourceIdentity(), true));
        assertEquals(1, repository.followed(Long.MAX_VALUE, 100).size());
    }

    private ReactionSample sample() {
        ReactionSample sample = new ReactionSample();
        sample.setMajorEventId(5L);
        sample.setTitle("contract");
        sample.setSourceOriginType("NEWS_ITEM");
        sample.setSourceOriginKey("news:5");
        sample.setRegisteredAt(now);
        sample.setFirstCapturedAt(now.minusDays(2));
        return sample;
    }
}
