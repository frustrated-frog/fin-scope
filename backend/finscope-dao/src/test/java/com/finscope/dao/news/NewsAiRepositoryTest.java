package com.finscope.dao.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsAiJobState;
import com.finscope.common.enums.news.NewsAiRelation;
import com.finscope.common.enums.news.NewsAiInputMode;
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
        assertTrue(repository.reserve("one", "luna", 80, 100L, 3, now));
        assertFalse(repository.reserve("two", "luna", 30, 100L, 3, now));
        repository.settle("one", 10L, 2L, 20L, null);
        assertTrue(repository.reserve("two", "luna", 70, 100L, 3, now));
        repository.settle("two", null, null, null, "TIMEOUT");
        assertFalse(repository.reserve("three", "luna", 11, 100L, 3, now));
        assertEquals(70L, ((Number) repository.dailyUsage(now).get("unresolved_micros")).longValue());
        assertTrue(repository.reserve("tomorrow", "luna", 100, 100L, 3, now.plusDays(1)));
    }

    @Test
    void absentDailyLimitsKeepAcceptingCallsAndRecordingUsage() {
        assertTrue(repository.reserve("one", "luna", 2_000_000L, null, null, now));
        repository.settle("one", 200L, 100L, 2_000_000L, null);
        assertTrue(repository.reserve("two", "luna", 3_000_000L, null, null, now));
        repository.settle("two", null, null, null, "UNKNOWN");
        assertTrue(repository.reserve("three", "luna", 8_000_000L, null, null, now));
        var usage = repository.dailyUsage(now);
        assertEquals(3L, ((Number) usage.get("calls")).longValue());
        assertEquals(2_000_000L, ((Number) usage.get("actual_micros")).longValue());
        assertEquals(11_000_000L, ((Number) usage.get("unresolved_micros")).longValue());
    }

    @Test
    void optionalLimitsCanBeAppliedIndependentlyAndZeroStillStopsSending() {
        assertTrue(repository.reserve("amount1", "luna", 80, 100L, null, now));
        assertFalse(repository.reserve("amount2", "luna", 30, 100L, null, now));
        assertTrue(repository.reserve("count1", "luna", 500_000L, null, 2, now));
        assertFalse(repository.reserve("count2", "luna", 1L, null, 2, now));
        assertFalse(repository.reserve("zeroAmount", "luna", 1L, 0L, null, now));
        assertFalse(repository.reserve("zeroCalls", "luna", 1L, null, 0, now));
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
    void stableJobIdentityInheritsUnknownLegacyWorkAcrossPolicyChanges() {
        String occurrence = "body@2026-11-17";
        repository.claim("old-model-policy", occurrence, "one", now, 2);
        repository.attachCall("old-model-policy", "one", "sent");
        repository.fail("old-model-policy", "one", NewsAiJobState.UNKNOWN, now.plusDays(1));
        assertEquals("old-model-policy", repository.jobKey(occurrence));
        assertEquals("old-model-policy", repository.jobKey(occurrence));
        assertFalse(repository.claim(repository.jobKey(occurrence), occurrence, "new-model", now.plusDays(2), 2));
        assertFalse(repository.claimUnderstanding("body@2026-11-18", "next", now, 2));
    }

    @Test
    void understandingLeaseIsSharedAcrossDatesAndDispatchedUnknownNeverResends() {
        var first = item("body@2026-11-17", "one", NewsAiInputMode.UNDERSTAND);
        assertTrue(repository.claim(first.getJobKey(), first.getOccurrenceKey(), "one", now, 2));
        assertTrue(repository.claimUnderstanding(first.getOccurrenceKey(), "one", now, 2));
        assertFalse(repository.claimUnderstanding("body@2026-11-18", "two", now, 2));
        assertTrue(repository.reserve("call", "luna", 100, null, null, now));
        assertEquals(Boolean.TRUE, transaction.execute(status -> repository.attachDispatch("call", List.of(first), "v3", 1000, 3, 1, now)));
        assertFalse(repository.claimUnderstanding("body@2026-11-18", "two", now.plusMinutes(6), 2));
        assertEquals("UNKNOWN", jdbc.queryForObject("SELECT state FROM news_ai_understanding", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT attempt FROM news_ai_understanding", Integer.class));
    }

    @Test
    void cancelledBatchDoesNotPartiallyAttachOrConsumeAttemptsAndAuditCountsOnlySentWork() {
        var first = item("one@2026-11-17", "one", NewsAiInputMode.UNDERSTAND);
        var second = item("two@2026-11-17", "wrong-owner", NewsAiInputMode.UNDERSTAND);
        repository.claim(first.getJobKey(), first.getOccurrenceKey(), "one", now, 2);
        repository.claimUnderstanding(first.getOccurrenceKey(), "one", now, 2);
        repository.claim(second.getJobKey(), second.getOccurrenceKey(), "actual-owner", now, 2);
        repository.reserve("cancel", "luna", 100, null, null, now);
        assertEquals(Boolean.FALSE, transaction.execute(status -> repository.attachDispatch("cancel", List.of(first, second), "v3", 1000, 0, 0, now)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM news_ai_content WHERE call_id IS NOT NULL", Integer.class));
        transaction.executeWithoutResult(status -> repository.cancelDispatch("cancel", List.of(first, second), now));
        assertEquals(0, jdbc.queryForObject("SELECT attempt FROM news_ai_content WHERE cache_key=?", Integer.class, first.getJobKey()));
        assertEquals(0, repository.dailyEfficiency(now).getItemsSent());
        assertTrue(repository.claim(first.getJobKey(), first.getOccurrenceKey(), "one", now.plusMinutes(6), 2));
        assertTrue(repository.claimUnderstanding(first.getOccurrenceKey(), "one", now.plusMinutes(6), 2));
        repository.reserve("real", "luna", 100, null, null, now.plusMinutes(6));
        assertEquals(Boolean.TRUE, transaction.execute(status -> repository.attachDispatch("real", List.of(first), "v3", 1000, 6, 1, now.plusMinutes(6))));
        repository.settle("real", 20L, 10L, 7L, null);
        var usage = repository.dailyEfficiency(now);
        assertEquals(1, usage.getFullTextItems());
        assertEquals(1000, usage.getRequestBytes());
        assertEquals(6, usage.getCandidateReferences());
        assertEquals(1, usage.getUniqueCandidateCards());
    }

    @Test
    void understandingSurvivesEventConflictAndPruningPreservesUnknownProtection() {
        String occurrence = "body@2026-11-17";
        String key = repository.jobKey(occurrence);
        repository.claim(key, occurrence, "one", now, 2);
        repository.claimUnderstanding(occurrence, "one", now, 2);
        var analysis = new NewsAiAnalysis();
        analysis.setTitle("已理解");
        assertEquals(Boolean.TRUE, transaction.execute(status -> repository.saveAnalysis(key, "one", analysis, now)));
        assertEquals("已理解", repository.reusableAnalysis("body@2026-11-18").getTitle());
        repository.claim("unknown", "uncertain@2026-11-17", "two", now, 2);
        repository.fail("unknown", "two", NewsAiJobState.UNKNOWN, now);
        jdbc.execute("CREATE TABLE news_report(id TEXT,content_version INTEGER)");
        jdbc.execute("CREATE TABLE news_ai_report(report_id TEXT,content_version INTEGER,content_key TEXT,event_key TEXT)");
        transaction.executeWithoutResult(status -> repository.prune(now.plusDays(100)));
        assertEquals("unknown", repository.jobKey("uncertain@2026-11-17"));
        assertFalse(repository.claim("unknown", "uncertain@2026-11-17", "three", now.plusDays(101), 2));
    }

    private NewsAiDispatchItem item(String occurrence, String owner, NewsAiInputMode mode) {
        var item = new NewsAiDispatchItem();
        item.setJobKey(repository.jobKey(occurrence));
        item.setOccurrenceKey(occurrence);
        item.setOwner(owner);
        item.setMode(mode);
        item.setSourceChars(100);
        item.setSentChars(mode == NewsAiInputMode.UNDERSTAND ? 100 : 0);
        return item;
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
