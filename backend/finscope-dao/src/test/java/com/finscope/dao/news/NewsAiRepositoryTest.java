package com.finscope.dao.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsAiJobState;
import com.finscope.common.enums.news.NewsAiRelation;
import com.finscope.domain.news.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NewsAiRepositoryTest {
    @TempDir Path directory;
    private NewsAiRepository repository;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private final LocalDateTime now = LocalDateTime.parse("2026-11-17T10:00:00");

    @BeforeEach
    void setup() {
        SQLiteDataSource source = new SQLiteDataSource(); source.setUrl("jdbc:sqlite:" + directory.resolve("news.db"));
        jdbc = new JdbcTemplate(source); transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE news_report_version(report_id TEXT,detected_at TEXT)");
        repository = new NewsAiRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        ReflectionTestUtils.setField(repository, "mapper", new ObjectMapper().findAndRegisterModules());
        repository.afterPropertiesSet(); repository.afterPropertiesSet();
    }

    @Test
    void ownershipAndRestartDoNotResendUnknownRequests() {
        assertTrue(repository.claim("a", "hash", "worker1", now, 2));
        assertFalse(repository.claim("a", "hash", "worker2", now, 2));
        assertTrue(repository.attachCall("a", "worker1", "sent"));
        assertFalse(repository.claim("a", "hash", "worker2", now.plusMinutes(6), 2));
        assertEquals("UNKNOWN", jdbc.queryForObject("SELECT state FROM news_ai_content", String.class));
        assertTrue(repository.claim("b", "other", "worker1", now, 2));
        assertTrue(repository.claim("b", "other", "worker2", now.plusMinutes(6), 2));
        assertFalse(repository.saveAnalysis("b", "worker1", new NewsAiAnalysis(), now));
    }

    @Test
    void reservationIncludesOutstandingCallsAndKnownUsageReleasesUnusedBudget() {
        assertTrue(repository.reserve("one", "luna", 80, 100, 3, now));
        assertFalse(repository.reserve("two", "luna", 30, 100, 3, now));
        repository.settle("one", 10L, 2L, 20L, null);
        assertTrue(repository.reserve("two", "luna", 70, 100, 3, now));
        repository.settle("two", null, null, null, "TIMEOUT");
        assertFalse(repository.reserve("three", "luna", 11, 100, 3, now));
        assertEquals(70L, ((Number) repository.dailyUsage(now).get("unresolved_micros")).longValue());
        assertTrue(repository.reserve("tomorrow", "luna", 100, 100, 3, now.plusDays(1)));
    }

    @Test
    void bindingUsesEventVersionAndKeepsAnalysisAfterAConflict() {
        assertTrue(repository.claim("a", "hash", "owner", now, 2));
        var analysis = new NewsAiAnalysis(); analysis.setTitle("第一条新闻");
        assertTrue(repository.saveAnalysis("a", "owner", analysis, now));
        var event = new NewsAiEvent(); event.setEventKey("stable"); event.setRevision(1); event.setAnalysis(analysis);
        var binding = new NewsAiBinding(); binding.setEventKey("stable"); binding.setContentKey("hash"); binding.setRelation(NewsAiRelation.NEW_EVENT); binding.setAnalysis(analysis);
        assertEquals(Boolean.TRUE, transaction.execute(status -> repository.bind("a", "owner", binding, event, 0, now)));
        assertTrue(repository.claim("b", "changed", "owner2", now, 2));
        repository.saveAnalysis("b", "owner2", analysis, now);
        event.setRevision(2);
        assertEquals(Boolean.FALSE, transaction.execute(status -> repository.bind("b", "owner2", binding, event, 0, now)));
        assertNotNull(repository.analysis("b"));
        assertEquals(1, repository.event("stable").getRevision());
        assertEquals(1, repository.bindings(List.of("a", "b")).size());
        assertEquals(Boolean.TRUE, transaction.execute(status -> repository.bind("b", "owner2", binding, event, 1, now)));
    }

    @Test
    void togglingModeResetsTheIncrementalCutoffWithoutDeletingHistory() {
        assertEquals(now, transaction.execute(status -> repository.observeMode(true, now)));
        assertEquals(now, transaction.execute(status -> repository.observeMode(true, now.plusHours(1))));
        transaction.execute(status -> repository.observeMode(false, now.plusHours(2)));
        assertEquals(now.plusHours(3), transaction.execute(status -> repository.observeMode(true, now.plusHours(3))));
    }

    @Test
    void attemptsAndCooldownBoundInvalidOutputRetries() {
        assertTrue(repository.claim("a", "hash", "owner", now, 2));
        repository.fail("a", "owner", NewsAiJobState.RETRYABLE, now.plusHours(1));
        assertFalse(repository.claim("a", "hash", "next", now.plusMinutes(30), 2));
        assertTrue(repository.claim("a", "hash", "next", now.plusHours(1), 2));
        repository.fail("a", "next", NewsAiJobState.RETRYABLE, now.plusHours(2));
        assertFalse(repository.claim("a", "hash", "last", now.plusHours(3), 2));
    }
}
