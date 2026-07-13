package com.finscope.dao.knowledge;

import com.finscope.common.util.TimeUtil;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KnowledgeQueryRepositoryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 12, 0);

    @TempDir
    Path tempDir;

    private RecordingJdbcTemplate jdbc;
    private KnowledgeQueryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db") + "?foreign_keys=on");
        jdbc = new RecordingJdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        new KnowledgeSchemaMigrator(jdbc, new DataSourceTransactionManager(dataSource)).migrate();
        repository = new KnowledgeQueryRepository(jdbc);
        insertFixture();
        jdbc.clearRecordedSql();
    }

    @Test
    void overviewCountsOnlyAcceptedTasksAndKeepsEveryCollectionBounded() {
        KnowledgeQueryRepository.OverviewSnapshot snapshot = repository.loadOverview(NOW);

        assertEquals(3, snapshot.getAcceptedTaskCount());
        assertEquals(4, snapshot.getSuggestedTaskCount());
        assertEquals(1, snapshot.getDueReviewCount());
        assertEquals(8, snapshot.getActiveTopics().size());
        assertEquals(10, snapshot.getRecentEntries().size());
        assertFalse(snapshot.getActionCandidates().isEmpty());
        assertFalse(jdbc.recordedSql().toLowerCase().contains("article.body"));
        assertFalse(jdbc.recordedSql().toLowerCase().contains(" a.body"));
    }

    @Test
    void topicAndTaskQueriesReturnBoundedPagesWithTotalCounts() {
        PageResponse<Topic> topics = repository.findTopicsPage(
                "ACTIVE", null, false, null, 0, 5, NOW);
        PageResponse<LearningTask> tasks = repository.findLearningTasksPage(
                "SUGGESTED", null, null, 0, 2);

        assertEquals(5, topics.getItems().size());
        assertEquals(10, topics.getTotalCount());
        assertEquals(2, topics.getTotalPages());
        assertEquals(2, tasks.getItems().size());
        assertEquals(4, tasks.getTotalCount());
        assertEquals(2, tasks.getTotalPages());
        assertFalse(jdbc.recordedSql().toLowerCase().contains("body"));
    }

    @Test
    void newEvidenceActionRequiresAnActualEvidenceRecordAfterLastReview() {
        KnowledgeQueryRepository.OverviewSnapshot beforeEvidence = repository.loadOverview(NOW);
        assertFalse(actionTypes(beforeEvidence).contains("CHECK_NEW_EVIDENCE"));

        jdbc.update("INSERT INTO evidence_item(event_id,article_id,source_tier,evidence_type," +
                        "claim,claim_key,confidence,created_at) VALUES(?,?,?,?,?,?,?,?)",
                301, 401, "A", "FACT", "New verified evidence", "new verified evidence",
                90, TimeUtil.text(NOW.minusMinutes(30)));

        KnowledgeQueryRepository.OverviewSnapshot afterEvidence = repository.loadOverview(NOW);
        assertFalse(afterEvidence.getActionCandidates().isEmpty());
        assertEquals(1, actionTypes(afterEvidence).stream()
                .filter("CHECK_NEW_EVIDENCE"::equals)
                .count());
    }

    private List<String> actionTypes(KnowledgeQueryRepository.OverviewSnapshot snapshot) {
        return snapshot.getActionCandidates().stream()
                .map(candidate -> candidate.getType())
                .collect(Collectors.toList());
    }

    private void insertFixture() {
        String now = TimeUtil.text(NOW);
        for (int index = 1; index <= 11; index++) {
            String lifecycle = index == 11 ? "ARCHIVED" : "ACTIVE";
            jdbc.update("INSERT INTO topic(id,name,slug,status,lifecycle_status,mastery_status," +
                            "revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    index, "Topic " + index, "topic-" + index, "LEARNING", lifecycle,
                    "BUILDING", 0, now, TimeUtil.text(NOW.minusMinutes(index)));
        }
        insertTask(101, 1, "IN_PROGRESS", 80, now);
        insertTask(102, 2, "TODO", 90, now);
        insertTask(103, 3, "TODO", 40, now);
        for (int index = 0; index < 4; index++) {
            insertTask(200 + index, null, "SUGGESTED", 50, now);
        }
        for (int index = 0; index < 12; index++) {
            jdbc.update("INSERT INTO knowledge_entry(topic_id,entry_type,entry_status,content_markdown," +
                            "confidence,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                    1, "INSIGHT", "FINAL", "Entry " + index, "MEDIUM", 0,
                    TimeUtil.text(NOW.minusMinutes(index)), now);
        }
        jdbc.update("INSERT INTO topic_review_state(topic_id,last_reviewed_at,next_review_at," +
                        "interval_days,review_count,revision) VALUES(?,?,?,?,?,?)",
                2, TimeUtil.text(NOW.minusDays(30)), TimeUtil.text(NOW.minusDays(1)), 30, 1, 0);
        jdbc.update("INSERT INTO event_cluster(id,canonical_title,canonical_event_key,theme_code,status," +
                        "first_seen_at,last_seen_at,last_meaningful_update_at,importance_score,novelty_state," +
                        "evidence_count,article_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                301, "New evidence", "new-evidence", "AI", "ACTIVE", now, now,
                TimeUtil.text(NOW.minusHours(1)), 75, "UPDATE", 1, 1, now, now);
        jdbc.update("INSERT INTO topic_event(topic_id,event_id,link_type,created_at) VALUES(?,?,?,?)",
                3, 301, "RELATED", now);
        jdbc.update("INSERT INTO article(id,title,url,body,fetched_at) VALUES(?,?,?,?,?)",
                401, "Large article", "https://example.test/article", "BODY_MUST_NOT_LOAD", now);
    }

    private void insertTask(long id, Integer topicId, String status, int priority, String now) {
        jdbc.update("INSERT INTO learning_task(id,topic_id,theme_code,question,difficulty,status," +
                        "origin,priority,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id, topicId, "AI", "Question " + id, "FOUNDATION", status,
                status.equals("SUGGESTED") ? "AGENT" : "USER", priority, 0, now, now);
    }

    static class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> statements = new ArrayList<String>();

        RecordingJdbcTemplate(SQLiteDataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            statements.add(sql);
            return super.query(sql, rowMapper, args);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            statements.add(sql);
            return super.queryForObject(sql, requiredType, args);
        }

        void clearRecordedSql() {
            statements.clear();
        }

        String recordedSql() {
            return String.join("\n", statements);
        }
    }
}
