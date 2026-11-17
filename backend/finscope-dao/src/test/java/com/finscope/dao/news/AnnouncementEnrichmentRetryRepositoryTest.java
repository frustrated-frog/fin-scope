package com.finscope.dao.news;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementEnrichmentRetryRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void persistsBackoffAcrossInstancesAndResetsOnContentChangeOrSuccess() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("retry.db"));
        AnnouncementEnrichmentRetryRepository first = repository(source);
        LocalDateTime now = LocalDateTime.parse("2026-11-17T10:00:00");
        assertTrue(first.canAttempt("AN1", "original", now));
        first.recordFailure("AN1", "original", "BODY_READ_FAILED", true, now);
        AnnouncementEnrichmentRetryRepository restarted = repository(source);
        assertFalse(restarted.canAttempt("AN1", "original", now.plusMinutes(4)));
        assertTrue(restarted.canAttempt("AN1", "original", now.plusMinutes(5)));
        assertTrue(restarted.canAttempt("AN1", "new title", now));
        restarted.recordFailure("AN1", "original", "BODY_READ_FAILED", true, now.plusMinutes(5));
        assertFalse(restarted.canAttempt("AN1", "original", now.plusMinutes(14)));
        assertTrue(restarted.canAttempt("AN1", "original", now.plusMinutes(15)));
        restarted.markSuccess("AN1");
        assertTrue(first.canAttempt("AN1", "original", now));
        restarted.recordFailure("AN1", "original", "ISSUER_MISMATCH", false, now);
        assertFalse(first.canAttempt("AN1", "original", now.plusHours(5)));
        assertTrue(first.canAttempt("AN1", "original", now.plusHours(6)));
    }

    private AnnouncementEnrichmentRetryRepository repository(SQLiteDataSource source) {
        AnnouncementEnrichmentRetryRepository repository = new AnnouncementEnrichmentRetryRepository();
        ReflectionTestUtils.setField(repository, "jdbc", new JdbcTemplate(source));
        repository.afterPropertiesSet();
        return repository;
    }
}
