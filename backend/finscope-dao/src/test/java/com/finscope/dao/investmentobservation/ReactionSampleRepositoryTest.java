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
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
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

    @Test
    void permanentProviderFailureStopsAutomaticAttemptsButKeepsManualRefreshPossible() {
        var sample = sample();
        sample.setState(ReactionSampleState.OBSERVING);
        sample.setPublishedAt(now.minusDays(30));
        var saved = repository.create(sample);
        assertTrue(repository.saveFailure(saved.getId(), 0, "provider unavailable", now));
        assertTrue(repository.findDue(now.plusDays(1), 20).isEmpty());
        assertTrue(repository.saveCalculation(saved.getId(), 1, new ReactionCalculation(), now.plusDays(1)));
    }

    @Test
    void newChannelReusesLegacyEventIdentityAndDoesNotCreateAnotherSample() {
        var original = sample();
        original.setPublishedAt(now);
        original.setSourceIdentity("NEWS:legacy");
        original.setState(ReactionSampleState.OBSERVING);
        original.setInstrumentCode("600519.SH");
        var saved = repository.create(original);
        var incoming = sample();
        incoming.setPublishedAt(now.plusMinutes(10));
        incoming.setSourceIdentity("EVENT:new-rule-hash");
        incoming.setSourceOriginType("RADAR_SIGNAL");
        incoming.setSourceOriginKey("another-provider:5");
        assertFalse(repository.captureSource(incoming));
        assertEquals(1, repository.list(null, 0, 100).size());
        assertEquals(saved.getId(), repository.findByIdentity("NEWS:legacy").get(0).getId());
        assertEquals(1, repository.sources("NEWS:legacy").size());
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
    @Test
    void eventPaginationSearchAndCountsCoverBeyondOneHundredSamples() {
        for (int i = 0; i < 125; i++) {
            ReactionSample item = sample();
            item.setSourceIdentity("EVENT:" + i);
            item.setInstrumentCode("600519.SH");
            item.setState(i == 0 ? ReactionSampleState.ARCHIVED : ReactionSampleState.OBSERVING);
            item.setTitle(i == 0 ? "最早的归档合同" : "合同" + i);
            repository.create(item);
        }
        var queries = new ReactionEventQueryRepository();
        ReflectionTestUtils.setField(queries, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(queries, "samples", repository);
        var query = new com.finscope.domain.investmentobservation.ReactionEventQuery();
        var first = queries.query(query);
        assertEquals(124, first.getTotal());
        assertEquals(20, first.getItems().size());
        assertEquals(1L, first.getCounts().get("HISTORY"));
        query.setPage(7);
        assertEquals(4, queries.query(query).getItems().size());
        query.setPage(1);
        query.setView(com.finscope.common.enums.investmentobservation.ReactionWorkspaceView.HISTORY);
        query.setQuery("最早的");
        assertEquals("最早的归档合同", queries.query(query).getItems().get(0).getTitle());
        ReactionSample peer = sample();
        peer.setSourceIdentity("EVENT:0");
        peer.setInstrumentCode("000001.SZ");
        peer.setState(ReactionSampleState.ARCHIVED);
        peer.setTitle("最早的归档合同");
        repository.create(peer);
        query.setAnchor(Long.MAX_VALUE);
        var grouped = queries.query(query);
        assertEquals(1, grouped.getTotal());
        assertEquals(2, grouped.getStockCounts().get("EVENT:0"));
    }

    @Test
    void promotionKeepsOriginalLinkAndVersionsDoNotOverwriteObservedFacts() {
        ReactionSample proposed = sample();
        proposed.setSourceIdentity("EVENT:stable");
        proposed.setSourceOriginType("NEWS_ITEM");
        proposed.setSourceOriginKey("news-1");
        proposed.setAutomatic(true);
        proposed.setTitle("初始合同");
        proposed.setSummary("初始材料");
        repository.captureSource(proposed);
        ReactionSample draft = repository.findByIdentity("EVENT:stable").get(0);
        long originalId = draft.getId();
        proposed.setTitle("贵州茅台签订合同");
        proposed.setSummary("补全后的公司材料");
        repository.captureSource(proposed);
        draft = repository.findById(originalId).orElseThrow();
        assertEquals(proposed.getTitle(), draft.getTitle());
        assertEquals(2, repository.sourceVersions("EVENT:stable").size());
        repository.captureSource(proposed);
        assertEquals(2, repository.sourceVersions("EVENT:stable").size());
        ReactionSample resolved = sample();
        resolved.setSourceIdentity("EVENT:stable");
        resolved.setTitle(draft.getTitle());
        resolved.setInstrumentCode("600519.SH");
        resolved.setState(ReactionSampleState.OBSERVING);
        assertTrue(repository.promoteDraft(draft, java.util.List.of(resolved)));
        assertEquals("600519.SH", repository.findById(originalId).orElseThrow().getInstrumentCode());
        proposed.setTitle("贵州茅台终止合同");
        repository.captureSource(proposed);
        assertEquals("贵州茅台签订合同", repository.findById(originalId).orElseThrow().getTitle());
        assertEquals(3, repository.sourceVersions("EVENT:stable").size());
        ReactionSample observed = repository.findById(originalId).orElseThrow();
        assertTrue(repository.exclude(originalId, observed.getRevision(), true));
        assertTrue(repository.findDue(now.plusDays(1), 20).isEmpty());
    }

}
