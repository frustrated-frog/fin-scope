package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.LearningTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class LearningTaskRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<LearningTask> mapper = (rs, rowNum) -> {
        LearningTask task = new LearningTask();
        task.setId(rs.getLong("id"));
        task.setEventId(rs.getLong("event_id"));
        task.setThemeCode(rs.getString("theme_code"));
        task.setQuestion(rs.getString("question"));
        task.setConcepts(rs.getString("concepts"));
        task.setDifficulty(rs.getString("difficulty"));
        task.setStatus(rs.getString("status"));
        task.setWhyNeeded(rs.getString("why_needed"));
        task.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        task.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return task;
    };

    public LearningTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LearningTask save(LearningTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO learning_task(event_id,theme_code,question,concepts,difficulty,status,why_needed,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (task.getEventId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, task.getEventId());
            }
            ps.setString(2, task.getThemeCode());
            ps.setString(3, task.getQuestion());
            ps.setString(4, task.getConcepts());
            ps.setString(5, task.getDifficulty());
            ps.setString(6, task.getStatus());
            ps.setString(7, task.getWhyNeeded());
            ps.setString(8, TimeUtil.text(task.getCreatedAt()));
            ps.setString(9, TimeUtil.text(task.getUpdatedAt()));
            return ps;
        }, keyHolder);
        task.setId(keyHolder.getKey().longValue());
        return task;
    }

    public List<LearningTask> findAll() {
        return jdbcTemplate.query("SELECT * FROM learning_task ORDER BY updated_at DESC, id DESC", mapper);
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
        jdbcTemplate.update("UPDATE learning_task SET status = ?, updated_at = ? WHERE id = ?",
                status, TimeUtil.text(LocalDateTime.now()), id);
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Learning task not found: " + id));
    }
}
