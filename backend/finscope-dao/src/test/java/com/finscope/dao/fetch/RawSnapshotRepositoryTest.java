package com.finscope.dao.fetch;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.fetch.RawSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RawSnapshotRepositoryTest {
    @TempDir
    Path tempDir;
    private RawSnapshotRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        repository = new RawSnapshotRepository(jdbc);
    }

    @Test
    void persistsStandaloneSnapshotWithNullableRunAndParseMetadata() {
        RawSnapshot snapshot = new RawSnapshot();
        snapshot.setPurpose("WEB_ARTICLE");
        snapshot.setMethod("GET");
        snapshot.setRequestUrl("https://example.com/article");
        snapshot.setFinalUrl("https://example.com/article");
        snapshot.setRequestHeadersJson("{\"Cookie\":\"***\"}");
        snapshot.setStatus("FETCHED");
        snapshot.setHttpStatus(200);
        snapshot.setContentType("text/html; charset=utf-8");
        snapshot.setCharsetName("UTF-8");
        snapshot.setBodyBytes(12);
        snapshot.setBodySha256("sha256");
        snapshot.setBodyPath("raw/acquisition/2026-07-28/sha256.html");
        snapshot.setAttemptCount(1);
        snapshot.setDurationMs(21L);
        snapshot.setPolicyVersion("acquisition-v1");
        snapshot.setParserVersion("pending");
        snapshot.setFetchedAt(LocalDateTime.of(2026, 7, 28, 14, 0));

        RawSnapshot loaded = repository.findById(repository.save(snapshot).getId())
                .orElseThrow(AssertionError::new);

        assertEquals("WEB_ARTICLE", loaded.getPurpose());
        assertEquals("{\"Cookie\":\"***\"}", loaded.getRequestHeadersJson());
        assertEquals(LocalDateTime.of(2026, 7, 28, 14, 0), loaded.getFetchedAt());
        assertNull(loaded.getFetchRunId());
        assertNull(loaded.getParsedAt());
    }
}
