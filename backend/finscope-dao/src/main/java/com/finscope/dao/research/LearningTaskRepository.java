package com.finscope.dao.research;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.knowledge.KnowledgeEnums;
import com.finscope.domain.research.LearningTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class LearningTaskRepository {
    private static final String INSERT_COLUMNS =
            "(event_id,topic_id,theme_code,question,concepts,difficulty,status," +
                    "why_needed,origin,task_key,priority,accepted_at,dismissed_reason,completion_mode," +
                    "revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<LearningTask> mapper = (rs, rowNum) -> {
        LearningTask task = new LearningTask();
        task.setId(rs.getLong("id"));
        task.setEventId(nullableLong(rs, "event_id"));
        task.setTopicId(nullableLong(rs, "topic_id"));
        task.setThemeCode(rs.getString("theme_code"));
        task.setQuestion(rs.getString("question"));
        task.setConcepts(rs.getString("concepts"));
        task.setDifficulty(rs.getString("difficulty"));
        task.setStatus(rs.getString("status"));
        task.setWhyNeeded(rs.getString("why_needed"));
        task.setOrigin(rs.getString("origin"));
        task.setTaskKey(rs.getString("task_key"));
        task.setPriority(rs.getInt("priority"));
        task.setAcceptedAt(TimeUtil.localDateTime(rs, "accepted_at"));
        task.setDismissedReason(rs.getString("dismissed_reason"));
        task.setCompletionMode(rs.getString("completion_mode"));
        task.setRevision(rs.getLong("revision"));
        task.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        task.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return task;
    };

    public LearningTask save(LearningTask task) {
        prepareForInsert(task);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> insertStatement(connection, task, false), keyHolder);
        task.setId(keyHolder.getKey().longValue());
        return task;
    }

    public boolean insertSuggestionIfAbsent(LearningTask task) {
        if (task == null || task.getEventId() == null || isBlank(task.getTaskKey())) {
            throw new BusinessException(BizErrorCode.AGENT_SUGGESTION_KEYS_REQUIRED);
        }
        task.setStatus(KnowledgeEnums.LearningStatus.SUGGESTED.name());
        task.setOrigin("AGENT");
        task.setPriority(50);
        prepareForInsert(task);
        return jdbcTemplate.update(connection -> insertStatement(connection, task, true)) == 1;
    }

    public List<LearningTask> findAll() {
        return jdbcTemplate.query("SELECT * FROM learning_task ORDER BY updated_at DESC, id DESC", mapper);
    }

    public List<LearningTask> findPage(String status, Long topicId, String query,
                                       int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(BizErrorCode.PAGE_REQUEST_INVALID_LEARNING_TASK);
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM learning_task WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status=?");
            arguments.add(status);
        }
        if (topicId != null) {
            sql.append(" AND topic_id=?");
            arguments.add(topicId);
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND lower(question) LIKE lower(?) ESCAPE '\\'");
            arguments.add("%" + escapeLike(query.trim()) + "%");
        }
        sql.append(" ORDER BY priority DESC, updated_at DESC, id DESC LIMIT ? OFFSET ?");
        arguments.add(pageSize);
        arguments.add(page * pageSize);
        return jdbcTemplate.query(sql.toString(), mapper, arguments.toArray());
    }

    public List<LearningTask> findByEventId(Long eventId) {
        return jdbcTemplate.query("SELECT * FROM learning_task WHERE event_id = ? ORDER BY id ASC", mapper, eventId);
    }

    public Optional<LearningTask> findById(Long id) {
        List<LearningTask> tasks = jdbcTemplate.query("SELECT * FROM learning_task WHERE id = ?", mapper, id);
        return tasks.isEmpty() ? Optional.<LearningTask>empty() : Optional.of(tasks.get(0));
    }

    public int countByEventId(Long eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_task WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_task", Integer.class);
        return count == null ? 0 : count;
    }

    public boolean transition(Long id, String expectedStatus, String targetStatus, Long topicId,
                              LocalDateTime acceptedAt, String dismissedReason,
                              String completionMode, long expectedRevision) {
        int updated = jdbcTemplate.update("UPDATE learning_task SET status=?, topic_id=?, accepted_at=?, " +
                        "dismissed_reason=?, completion_mode=?, revision=revision+1, updated_at=? " +
                        "WHERE id=? AND status=? AND revision=?",
                targetStatus, topicId, TimeUtil.text(acceptedAt), dismissedReason, completionMode,
                TimeUtil.text(LocalDateTime.now()), id, expectedStatus, expectedRevision);
        return updated == 1;
    }

    public int moveByEventId(Long sourceEventId, Long targetEventId) {
        return jdbcTemplate.update("UPDATE learning_task SET event_id=?, revision=revision+1, updated_at=? WHERE event_id=?",
                targetEventId, TimeUtil.text(LocalDateTime.now()), sourceEventId);
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void prepareForInsert(LearningTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (isBlank(task.getOrigin())) {
            task.setOrigin("AGENT");
        }
    }

    private PreparedStatement insertStatement(java.sql.Connection connection,
                                              LearningTask task,
                                              boolean ignoreConflict) throws java.sql.SQLException {
        String sql = (ignoreConflict ? "INSERT OR IGNORE INTO learning_task" :
                "INSERT INTO learning_task") + INSERT_COLUMNS;
        PreparedStatement statement = ignoreConflict
                ? connection.prepareStatement(sql)
                : connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        if (task.getEventId() == null) {
            statement.setObject(1, null);
        } else {
            statement.setLong(1, task.getEventId());
        }
        if (task.getTopicId() == null) {
            statement.setObject(2, null);
        } else {
            statement.setLong(2, task.getTopicId());
        }
        statement.setString(3, task.getThemeCode());
        statement.setString(4, task.getQuestion());
        statement.setString(5, task.getConcepts());
        statement.setString(6, task.getDifficulty());
        statement.setString(7, task.getStatus());
        statement.setString(8, task.getWhyNeeded());
        statement.setString(9, task.getOrigin());
        statement.setString(10, task.getTaskKey());
        statement.setInt(11, task.getPriority());
        statement.setString(12, TimeUtil.text(task.getAcceptedAt()));
        statement.setString(13, task.getDismissedReason());
        statement.setString(14, task.getCompletionMode());
        statement.setLong(15, task.getRevision());
        statement.setString(16, TimeUtil.text(task.getCreatedAt()));
        statement.setString(17, TimeUtil.text(task.getUpdatedAt()));
        return statement;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
