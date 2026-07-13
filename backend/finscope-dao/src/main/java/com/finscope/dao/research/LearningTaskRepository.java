package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
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
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (task.getOrigin() == null || task.getOrigin().isEmpty()) {
            task.setOrigin("AGENT");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO learning_task(event_id,topic_id,theme_code,question,concepts,difficulty,status," +
                            "why_needed,origin,task_key,priority,accepted_at,dismissed_reason,completion_mode," +
                            "revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (task.getEventId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, task.getEventId());
            }
            if (task.getTopicId() == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, task.getTopicId());
            }
            ps.setString(3, task.getThemeCode());
            ps.setString(4, task.getQuestion());
            ps.setString(5, task.getConcepts());
            ps.setString(6, task.getDifficulty());
            ps.setString(7, task.getStatus());
            ps.setString(8, task.getWhyNeeded());
            ps.setString(9, task.getOrigin());
            ps.setString(10, task.getTaskKey());
            ps.setInt(11, task.getPriority());
            ps.setString(12, TimeUtil.text(task.getAcceptedAt()));
            ps.setString(13, task.getDismissedReason());
            ps.setString(14, task.getCompletionMode());
            ps.setLong(15, task.getRevision());
            ps.setString(16, TimeUtil.text(task.getCreatedAt()));
            ps.setString(17, TimeUtil.text(task.getUpdatedAt()));
            return ps;
        }, keyHolder);
        task.setId(keyHolder.getKey().longValue());
        return task;
    }

    public List<LearningTask> findAll() {
        return jdbcTemplate.query("SELECT * FROM learning_task ORDER BY updated_at DESC, id DESC", mapper);
    }

    public List<LearningTask> findPage(String status, Long topicId, String query,
                                       int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("Invalid learning task page request");
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

    public LearningTask updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE learning_task SET status=?, revision=revision+1, updated_at=? WHERE id=?",
                status, TimeUtil.text(LocalDateTime.now()), id);
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Learning task not found: " + id));
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
}
