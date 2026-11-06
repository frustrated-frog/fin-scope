package com.finscope.dao.watchlistresearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.common.enums.watchlistresearch.ResearchScanStage;
import com.finscope.domain.watchlistresearch.ResearchScanRun;
import com.finscope.domain.watchlistresearch.WatchlistResearchSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ResearchScanRepositoryTest {
    @TempDir
    Path directory;
    private ResearchScanRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("research.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        ResearchScanSchemaMigrator migrator = new ResearchScanSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbc", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager", new DataSourceTransactionManager(source));
        migrator.afterPropertiesSet();
        migrator.afterPropertiesSet();
        repository = new ResearchScanRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        ReflectionTestUtils.setField(repository, "json", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void activeSlotIsUniqueAcrossDifferentMemberships() {
        assertTrue(repository.tryStart(run("one", "members-a"), true));
        assertFalse(repository.tryStart(run("two", "members-b"), true));
        assertEquals("one", repository.active().orElseThrow().getId());
    }

    @Test
    void automaticBusinessKeySurvivesCompletionAndManualRefreshKeepsBothSnapshots() {
        ResearchScanRun first = run("one", "members");
        assertTrue(repository.tryStart(first, true));
        complete(first);
        assertFalse(repository.tryStart(run("two", "members"), true));
        ResearchScanRun manual = run("three", "members");
        manual.setStartedAt(first.getStartedAt().plusSeconds(1));
        assertTrue(repository.tryStart(manual, false));
        complete(manual);
        assertEquals(2, repository.history().size());
        assertEquals("three", repository.latestCompleted("members").orElseThrow().getId());
        assertEquals(LocalDate.of(2026, 11, 6), repository.find("one").orElseThrow().getSnapshot().getMarketDate());
        assertNull(repository.history().get(0).getSnapshot());
    }

    @Test
    void lateFailureAndProgressCannotOverwriteCompletedSnapshot() {
        ResearchScanRun run = run("one", "members");
        repository.tryStart(run, true);
        complete(run);
        run.setStatus(ResearchScanStatus.FAILED);
        run.setMessage("late failure");
        assertFalse(repository.finish(run));
        repository.progress(run.getId(), ResearchScanStage.MARKET, 100, "late progress");
        assertEquals(ResearchScanStatus.READY, repository.find(run.getId()).orElseThrow().getStatus());
        assertNotEquals("late progress", repository.find(run.getId()).orElseThrow().getMessage());
    }

    @Test
    void restartMarksOnlyActiveRunInterruptedAndReleasesSlot() {
        ResearchScanRun done = run("done", "members");
        repository.tryStart(done, true);
        complete(done);
        repository.tryStart(run("active", "other"), true);
        repository.recoverInterrupted();
        assertTrue(repository.active().isEmpty());
        assertEquals(ResearchScanStatus.FAILED, repository.find("active").orElseThrow().getStatus());
        assertEquals(ResearchScanStatus.READY, repository.find("done").orElseThrow().getStatus());
        assertTrue(repository.tryStart(run("retry", "other"), false));
    }

    @Test
    void refusesInvalidTerminalPayloads() {
        ResearchScanRun run = run("one", "members");
        assertThrows(IllegalArgumentException.class, () -> repository.finish(run));
        run.setStatus(ResearchScanStatus.READY);
        assertThrows(IllegalArgumentException.class, () -> repository.finish(run));
        assertTrue(repository.find("missing").isEmpty());
    }

    private void complete(ResearchScanRun run) {
        run.setStatus(ResearchScanStatus.READY);
        run.setFinishedAt(run.getStartedAt().plusSeconds(1));
        WatchlistResearchSnapshot snapshot = new WatchlistResearchSnapshot();
        snapshot.setMarketDate(run.getScanDate());
        snapshot.setObservedAt(run.getFinishedAt());
        run.setSnapshot(snapshot);
        assertTrue(repository.finish(run));
    }

    private ResearchScanRun run(String id, String membership) {
        ResearchScanRun result = new ResearchScanRun();
        result.setId(id);
        result.setMembershipKey(membership);
        result.setScanDate(LocalDate.of(2026, 11, 6));
        result.setStatus(ResearchScanStatus.RUNNING);
        result.setStartedAt(Instant.parse("2026-11-07T02:00:00Z"));
        result.setMessage("扫描中");
        result.setTotalStocks(2);
        return result;
    }
}
