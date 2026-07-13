package com.finscope.dao.knowledge;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.knowledge.TopicReviewState;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeAssetRepositoryTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private KnowledgeEntryRepository entries;
    private TopicEventRepository topicEvents;
    private TopicReviewStateRepository reviewStates;
    private KnowledgeProjectionJobRepository projectionJobs;
    private long topicId;
    private long taskId;
    private long evidenceId;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        new KnowledgeSchemaMigrator(jdbc, new DataSourceTransactionManager(dataSource)).migrate();

        entries = new KnowledgeEntryRepository(jdbc);
        topicEvents = new TopicEventRepository(jdbc);
        reviewStates = new TopicReviewStateRepository(jdbc);
        projectionJobs = new KnowledgeProjectionJobRepository(jdbc);
        seedAggregateRoots();
    }

    @Test
    void savesUpdatesAndFinalizesDraftWithEvidence() {
        KnowledgeEntry draft = entries.saveDraft(draft("initial answer"));

        assertTrue(entries.updateDraft(
                draft.getId(), "revised answer", "HIGH", draft.getRevision()));
        assertFalse(entries.updateDraft(
                draft.getId(), "stale answer", "LOW", draft.getRevision()));
        KnowledgeEntry revised = entries.findDraftByTaskId(taskId).orElseThrow(AssertionError::new);
        assertEquals("revised answer", revised.getContentMarkdown());
        assertEquals(1L, revised.getRevision());

        entries.linkEvidence(revised.getId(), Arrays.asList(evidenceId));
        assertEquals(Arrays.asList(evidenceId), entries.findEvidenceIds(revised.getId()));
        assertTrue(entries.finalizeDraft(revised.getId(), revised.getRevision()));
        assertEquals(1, entries.findFinalByTopicId(topicId, 20, 0).size());

        KnowledgeEntry duplicate = entries.saveDraft(draft("second answer"));
        assertThrows(DataAccessException.class,
                () -> entries.finalizeDraft(duplicate.getId(), duplicate.getRevision()));
    }

    @Test
    void linksTopicEventAndSchedulesReviewIdempotently() {
        assertTrue(topicEvents.link(topicId, 1L, "RELATED"));
        assertFalse(topicEvents.link(topicId, 1L, "RELATED"));
        assertEquals(1, topicEvents.findEventIds(topicId).size());

        TopicReviewState state = reviewStates.createIfAbsent(topicId);
        assertEquals(7, state.getIntervalDays());
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 7, 13, 11, 0);
        assertTrue(reviewStates.recordReview(
                topicId, reviewedAt, reviewedAt.plusDays(14), 14, state.getRevision()));
        assertFalse(reviewStates.recordReview(
                topicId, reviewedAt, reviewedAt.plusDays(30), 30, state.getRevision()));

        TopicReviewState updated = reviewStates.findByTopicId(topicId).orElseThrow(AssertionError::new);
        assertEquals(14, updated.getIntervalDays());
        assertEquals(1, updated.getReviewCount());
        assertEquals(1L, updated.getRevision());
    }

    @Test
    void enqueuesClaimsAndRecoversProjectionJob() {
        KnowledgeEntry draft = entries.saveDraft(draft("projection answer"));
        KnowledgeProjectionJob first = projectionJobs.enqueue(topicId, draft.getId());
        KnowledgeProjectionJob duplicate = projectionJobs.enqueue(topicId, draft.getId());
        assertEquals(first.getId(), duplicate.getId());

        assertTrue(projectionJobs.claim(first.getId()));
        assertFalse(projectionJobs.claim(first.getId()));
        projectionJobs.markFailed(first.getId(), "disk unavailable");
        KnowledgeProjectionJob failed = projectionJobs.findById(first.getId()).orElseThrow(AssertionError::new);
        assertEquals("FAILED", failed.getStatus());
        assertEquals("disk unavailable", failed.getLastError());
        assertEquals(1, failed.getAttemptCount());

        assertTrue(projectionJobs.claim(first.getId()));
        projectionJobs.markCompleted(first.getId());
        KnowledgeProjectionJob completed = projectionJobs.findById(first.getId()).orElseThrow(AssertionError::new);
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(2, completed.getAttemptCount());
    }

    private KnowledgeEntry draft(String markdown) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setTopicId(topicId);
        entry.setLearningTaskId(taskId);
        entry.setEntryType("ANSWER");
        entry.setQuestionSnapshot("What did we learn?");
        entry.setContentMarkdown(markdown);
        entry.setConfidence("MEDIUM");
        return entry;
    }

    private void seedAggregateRoots() {
        TopicRepository topicRepository = new TopicRepository();
        LearningTaskRepository taskRepository = new LearningTaskRepository();
        ReflectionTestUtils.setField(topicRepository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(taskRepository, "jdbcTemplate", jdbc);

        Topic topic = new Topic();
        topic.setName("AI Agents");
        topic.setSlug("ai-agents");
        topicId = topicRepository.save(topic).getId();

        jdbc.update("INSERT INTO event_cluster(" +
                        "id,canonical_title,canonical_event_key,theme_code,status,first_seen_at,last_seen_at," +
                        "importance_score,novelty_state,evidence_count,article_count,created_at,updated_at" +
                        ") VALUES(1,'Agent update','agent-update','AI','ACTIVE',?,?,80,'NEW',1,1,?,?)",
                "2026-07-13T09:00:00", "2026-07-13T09:00:00",
                "2026-07-13T09:00:00", "2026-07-13T09:00:00");
        jdbc.update("INSERT INTO article(id,title,url,fetched_at) VALUES(1,'source','https://example.com','2026-07-13T09:00:00')");
        jdbc.update("INSERT INTO evidence_item(" +
                        "event_id,article_id,source_tier,evidence_type,claim,confidence,created_at" +
                        ") VALUES(1,1,'A','FACT','Evidence claim',90,'2026-07-13T09:00:00')");
        evidenceId = jdbc.queryForObject("SELECT id FROM evidence_item", Long.class);

        LearningTask task = new LearningTask();
        task.setEventId(1L);
        task.setTopicId(topicId);
        task.setThemeCode("AI");
        task.setQuestion("What did we learn?");
        task.setDifficulty("MEDIUM");
        task.setStatus("IN_PROGRESS");
        taskId = taskRepository.save(task).getId();
    }
}
