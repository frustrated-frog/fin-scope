package com.finscope.dao.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.knowledge.KnowledgeActionCandidate;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded read projections for the knowledge workbench.
 *
 * <p>Overview queries intentionally never join article content. Large article
 * bodies remain behind detail endpoints and cannot inflate the home payload.</p>
 */
@Repository
public class KnowledgeQueryRepository {
    private static final int OVERVIEW_TOPIC_LIMIT = 8;
    private static final int OVERVIEW_ENTRY_LIMIT = 10;
    private static final int ACTION_CANDIDATE_LIMIT = 12;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Topic> topicMapper = (resultSet, rowNumber) -> {
        Topic topic = new Topic();
        topic.setId(resultSet.getLong("id"));
        topic.setName(resultSet.getString("name"));
        topic.setSlug(resultSet.getString("slug"));
        topic.setDescription(resultSet.getString("description"));
        topic.setStatus(resultSet.getString("status"));
        topic.setTerms(resultSet.getString("terms"));
        topic.setLearningQuestions(resultSet.getString("learning_questions"));
        topic.setLifecycleStatus(resultSet.getString("lifecycle_status"));
        topic.setMasteryStatus(resultSet.getString("mastery_status"));
        topic.setRevision(resultSet.getLong("revision"));
        topic.setArticleCount(resultSet.getInt("article_count"));
        topic.setBriefCount(resultSet.getInt("brief_count"));
        topic.setCreatedAt(TimeUtil.localDateTime(resultSet, "created_at"));
        topic.setUpdatedAt(TimeUtil.localDateTime(resultSet, "updated_at"));
        return topic;
    };

    private final RowMapper<KnowledgeEntry> entryMapper = (resultSet, rowNumber) -> {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(resultSet.getLong("id"));
        entry.setTopicId(resultSet.getLong("topic_id"));
        entry.setLearningTaskId(nullableLong(resultSet, "learning_task_id"));
        entry.setEntryType(resultSet.getString("entry_type"));
        entry.setEntryStatus(resultSet.getString("entry_status"));
        entry.setQuestionSnapshot(resultSet.getString("question_snapshot"));
        entry.setContentMarkdown(resultSet.getString("content_markdown"));
        entry.setConfidence(resultSet.getString("confidence"));
        entry.setRevision(resultSet.getLong("revision"));
        entry.setCreatedAt(TimeUtil.localDateTime(resultSet, "created_at"));
        entry.setUpdatedAt(TimeUtil.localDateTime(resultSet, "updated_at"));
        return entry;
    };

    private final RowMapper<LearningTask> taskMapper = (resultSet, rowNumber) -> {
        LearningTask task = new LearningTask();
        task.setId(resultSet.getLong("id"));
        task.setEventId(nullableLong(resultSet, "event_id"));
        task.setTopicId(nullableLong(resultSet, "topic_id"));
        task.setThemeCode(resultSet.getString("theme_code"));
        task.setQuestion(resultSet.getString("question"));
        task.setConcepts(resultSet.getString("concepts"));
        task.setDifficulty(resultSet.getString("difficulty"));
        task.setStatus(resultSet.getString("status"));
        task.setWhyNeeded(resultSet.getString("why_needed"));
        task.setOrigin(resultSet.getString("origin"));
        task.setTaskKey(resultSet.getString("task_key"));
        task.setPriority(resultSet.getInt("priority"));
        task.setAcceptedAt(TimeUtil.localDateTime(resultSet, "accepted_at"));
        task.setDismissedReason(resultSet.getString("dismissed_reason"));
        task.setCompletionMode(resultSet.getString("completion_mode"));
        task.setRevision(resultSet.getLong("revision"));
        task.setCreatedAt(TimeUtil.localDateTime(resultSet, "created_at"));
        task.setUpdatedAt(TimeUtil.localDateTime(resultSet, "updated_at"));
        return task;
    };

    public KnowledgeQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OverviewSnapshot loadOverview(LocalDateTime now) {
        String nowText = TimeUtil.text(now);
        OverviewSnapshot snapshot = loadMetrics(nowText);
        snapshot.setActiveTopics(jdbcTemplate.query(
                topicSelect() + " WHERE t.lifecycle_status='ACTIVE' " +
                        "ORDER BY t.updated_at DESC,t.id DESC LIMIT ?",
                topicMapper, OVERVIEW_TOPIC_LIMIT));
        snapshot.setRecentEntries(jdbcTemplate.query(
                "SELECT * FROM knowledge_entry WHERE entry_status='FINAL' " +
                        "ORDER BY created_at DESC,id DESC LIMIT ?",
                entryMapper, OVERVIEW_ENTRY_LIMIT));
        snapshot.setActionCandidates(loadActionCandidates(nowText));
        return snapshot;
    }

    public PageResponse<Topic> findTopicsPage(String lifecycle, String mastery,
                                               boolean dueOnly, String query,
                                               int page, int pageSize,
                                               LocalDateTime now) {
        validatePage(page, pageSize);
        SqlFilter filter = topicFilter(lifecycle, mastery, dueOnly, query, now);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic t LEFT JOIN topic_review_state trs ON trs.topic_id=t.id " +
                        filter.sql,
                Integer.class, filter.arguments.toArray());
        List<Object> pageArguments = new ArrayList<Object>(filter.arguments);
        pageArguments.add(pageSize);
        pageArguments.add(page * pageSize);
        List<Topic> items = jdbcTemplate.query(
                topicSelect() + " LEFT JOIN topic_review_state trs ON trs.topic_id=t.id " +
                        filter.sql + " ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?",
                topicMapper, pageArguments.toArray());
        return PageResponse.of(items, value(total), page, pageSize);
    }

    public PageResponse<LearningTask> findLearningTasksPage(String status, Long topicId,
                                                             String query, int page,
                                                             int pageSize) {
        validatePage(page, pageSize);
        SqlFilter filter = taskFilter(status, topicId, query);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_task lt " + filter.sql,
                Integer.class, filter.arguments.toArray());
        List<Object> pageArguments = new ArrayList<Object>(filter.arguments);
        pageArguments.add(pageSize);
        pageArguments.add(page * pageSize);
        List<LearningTask> items = jdbcTemplate.query(
                "SELECT lt.* FROM learning_task lt " + filter.sql +
                        " ORDER BY lt.priority DESC,lt.updated_at DESC,lt.id DESC LIMIT ? OFFSET ?",
                taskMapper, pageArguments.toArray());
        return PageResponse.of(items, value(total), page, pageSize);
    }

    private OverviewSnapshot loadMetrics(String nowText) {
        List<OverviewSnapshot> metrics = jdbcTemplate.query(
                "SELECT " +
                        "COALESCE(SUM(CASE WHEN status IN ('TODO','IN_PROGRESS') THEN 1 ELSE 0 END),0) " +
                        "AS accepted_task_count," +
                        "COALESCE(SUM(CASE WHEN status='SUGGESTED' THEN 1 ELSE 0 END),0) " +
                        "AS suggested_task_count," +
                        "(SELECT COUNT(*) FROM topic_review_state trs JOIN topic t ON t.id=trs.topic_id " +
                        " WHERE t.lifecycle_status='ACTIVE' AND trs.next_review_at IS NOT NULL " +
                        " AND trs.next_review_at<=?) AS due_review_count," +
                        "(SELECT COUNT(*) FROM topic WHERE lifecycle_status='ACTIVE') AS active_topic_count " +
                        "FROM learning_task",
                (resultSet, rowNumber) -> {
                    OverviewSnapshot value = new OverviewSnapshot();
                    value.setAcceptedTaskCount(resultSet.getInt("accepted_task_count"));
                    value.setSuggestedTaskCount(resultSet.getInt("suggested_task_count"));
                    value.setDueReviewCount(resultSet.getInt("due_review_count"));
                    value.setActiveTopicCount(resultSet.getInt("active_topic_count"));
                    return value;
                },
                nowText);
        return metrics.get(0);
    }

    private List<KnowledgeActionCandidate> loadActionCandidates(String nowText) {
        String sql = "SELECT * FROM (" +
                "SELECT 'CONTINUE_TASK' AS action_type,lt.id AS stable_id,lt.id AS task_id," +
                "lt.topic_id AS topic_id,lt.question AS title,lt.priority AS priority," +
                "lt.updated_at AS sort_at FROM learning_task lt WHERE lt.status='IN_PROGRESS' " +
                "UNION ALL " +
                "SELECT 'REVIEW_TOPIC',t.id,NULL,t.id,t.name,0,trs.next_review_at " +
                "FROM topic_review_state trs JOIN topic t ON t.id=trs.topic_id " +
                "WHERE t.lifecycle_status='ACTIVE' AND trs.next_review_at IS NOT NULL " +
                "AND trs.next_review_at<=? " +
                "UNION ALL " +
                "SELECT 'START_TASK',lt.id,lt.id,lt.topic_id,lt.question,lt.priority,lt.updated_at " +
                "FROM learning_task lt WHERE lt.status='TODO' " +
                "UNION ALL " +
                "SELECT 'CHECK_NEW_EVIDENCE',t.id,NULL,t.id,t.name," +
                "MAX(e.importance_score),MAX(ei.created_at) " +
                "FROM topic t JOIN topic_event te ON te.topic_id=t.id " +
                "JOIN event_cluster e ON e.id=te.event_id " +
                "JOIN evidence_item ei ON ei.event_id=e.id " +
                "LEFT JOIN topic_review_state trs ON trs.topic_id=t.id " +
                "WHERE t.lifecycle_status='ACTIVE' " +
                "AND ei.created_at>COALESCE(trs.last_reviewed_at,'0000-01-01 00:00:00') " +
                "AND (trs.next_review_at IS NULL OR trs.next_review_at>?) " +
                "GROUP BY t.id,t.name" +
                ") candidate ORDER BY CASE action_type " +
                "WHEN 'CONTINUE_TASK' THEN 1 WHEN 'REVIEW_TOPIC' THEN 2 " +
                "WHEN 'START_TASK' THEN 3 ELSE 4 END," +
                "priority DESC,sort_at ASC,stable_id ASC LIMIT ?";
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            KnowledgeActionCandidate candidate = new KnowledgeActionCandidate();
            candidate.setType(resultSet.getString("action_type"));
            candidate.setStableId(resultSet.getLong("stable_id"));
            candidate.setTaskId(nullableLong(resultSet, "task_id"));
            candidate.setTopicId(nullableLong(resultSet, "topic_id"));
            candidate.setTitle(resultSet.getString("title"));
            candidate.setPriority(resultSet.getInt("priority"));
            candidate.setSortAt(TimeUtil.localDateTime(resultSet, "sort_at"));
            return candidate;
        }, nowText, nowText, ACTION_CANDIDATE_LIMIT);
    }

    private SqlFilter topicFilter(String lifecycle, String mastery, boolean dueOnly,
                                  String query, LocalDateTime now) {
        SqlFilter filter = new SqlFilter("WHERE 1=1");
        addEquals(filter, "t.lifecycle_status", lifecycle);
        addEquals(filter, "t.mastery_status", mastery);
        if (dueOnly) {
            filter.sql.append(" AND trs.next_review_at IS NOT NULL AND trs.next_review_at<=?");
            filter.arguments.add(TimeUtil.text(now));
        }
        if (!isBlank(query)) {
            filter.sql.append(" AND (lower(t.name) LIKE lower(?) ESCAPE '\\' " +
                    "OR lower(COALESCE(t.description,'')) LIKE lower(?) ESCAPE '\\')");
            String pattern = "%" + escapeLike(query.trim()) + "%";
            filter.arguments.add(pattern);
            filter.arguments.add(pattern);
        }
        return filter;
    }

    private SqlFilter taskFilter(String status, Long topicId, String query) {
        SqlFilter filter = new SqlFilter("WHERE 1=1");
        addEquals(filter, "lt.status", status);
        if (topicId != null) {
            filter.sql.append(" AND lt.topic_id=?");
            filter.arguments.add(topicId);
        }
        if (!isBlank(query)) {
            filter.sql.append(" AND lower(lt.question) LIKE lower(?) ESCAPE '\\'");
            filter.arguments.add("%" + escapeLike(query.trim()) + "%");
        }
        return filter;
    }

    private void addEquals(SqlFilter filter, String column, String value) {
        if (!isBlank(value)) {
            filter.sql.append(" AND ").append(column).append("=?");
            filter.arguments.add(value);
        }
    }

    private String topicSelect() {
        return "SELECT t.*," +
                "(SELECT COUNT(*) FROM topic_article ta WHERE ta.topic_id=t.id) AS article_count," +
                "(SELECT COUNT(*) FROM topic_brief tb WHERE tb.topic_id=t.id) AS brief_count " +
                "FROM topic t";
    }

    private void validatePage(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(BizErrorCode.PAGE_REQUEST_INVALID_KNOWLEDGE);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static class SqlFilter {
        private final StringBuilder sql;
        private final List<Object> arguments = new ArrayList<Object>();

        private SqlFilter(String sql) {
            this.sql = new StringBuilder(sql);
        }
    }

    public static class OverviewSnapshot {
        private int acceptedTaskCount;
        private int suggestedTaskCount;
        private int dueReviewCount;
        private int activeTopicCount;
        private List<Topic> activeTopics;
        private List<KnowledgeEntry> recentEntries;
        private List<KnowledgeActionCandidate> actionCandidates;

        public int getAcceptedTaskCount() {
            return acceptedTaskCount;
        }

        public void setAcceptedTaskCount(int acceptedTaskCount) {
            this.acceptedTaskCount = acceptedTaskCount;
        }

        public int getSuggestedTaskCount() {
            return suggestedTaskCount;
        }

        public void setSuggestedTaskCount(int suggestedTaskCount) {
            this.suggestedTaskCount = suggestedTaskCount;
        }

        public int getDueReviewCount() {
            return dueReviewCount;
        }

        public void setDueReviewCount(int dueReviewCount) {
            this.dueReviewCount = dueReviewCount;
        }

        public int getActiveTopicCount() {
            return activeTopicCount;
        }

        public void setActiveTopicCount(int activeTopicCount) {
            this.activeTopicCount = activeTopicCount;
        }

        public List<Topic> getActiveTopics() {
            return activeTopics;
        }

        public void setActiveTopics(List<Topic> activeTopics) {
            this.activeTopics = activeTopics;
        }

        public List<KnowledgeEntry> getRecentEntries() {
            return recentEntries;
        }

        public void setRecentEntries(List<KnowledgeEntry> recentEntries) {
            this.recentEntries = recentEntries;
        }

        public List<KnowledgeActionCandidate> getActionCandidates() {
            return actionCandidates;
        }

        public void setActionCandidates(List<KnowledgeActionCandidate> actionCandidates) {
            this.actionCandidates = actionCandidates;
        }
    }
}
