package com.finscope.dao.task;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.task.AsyncTask;
import com.finscope.domain.task.TaskPhase;
import com.finscope.domain.task.TaskStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<AsyncTask> mapper = (rs, rowNum) -> {
        AsyncTask task = new AsyncTask();
        task.setId(rs.getString("id"));
        task.setType(rs.getString("type"));
        task.setStatus(rs.getString("status"));
        task.setPhase(rs.getString("phase"));
        task.setMessage(rs.getString("message"));
        task.setRequestUrl(rs.getString("request_url"));
        long articleId = rs.getLong("article_id");
        task.setArticleId(rs.wasNull() ? null : articleId);
        task.setErrorMessage(rs.getString("error_message"));
        task.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        task.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        task.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        task.setEndedAt(TimeUtil.localDateTime(rs, "ended_at"));
        return task;
    };

    public void create(AsyncTask task) {
        jdbcTemplate.update("INSERT INTO async_task(id,type,status,phase,message,request_url,article_id,error_message,"
                        + "created_at,updated_at,started_at,ended_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getPhase(),
                task.getMessage(),
                task.getRequestUrl(),
                task.getArticleId(),
                task.getErrorMessage(),
                TimeUtil.text(task.getCreatedAt()),
                TimeUtil.text(task.getUpdatedAt()),
                TimeUtil.text(task.getStartedAt()),
                TimeUtil.text(task.getEndedAt()));
    }

    public Optional<AsyncTask> findById(String id) {
        List<AsyncTask> tasks = jdbcTemplate.query("SELECT * FROM async_task WHERE id = ?", mapper, id);
        return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.get(0));
    }

    public void markRunning(String id, TaskPhase phase, String message) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE async_task SET status = ?, phase = ?, message = ?, started_at = COALESCE(started_at, ?),"
                        + " updated_at = ? WHERE id = ?",
                TaskStatus.RUNNING.name(),
                phase.name(),
                message,
                TimeUtil.text(now),
                TimeUtil.text(now),
                id);
    }

    public void updatePhase(String id, TaskPhase phase, String message) {
        jdbcTemplate.update("UPDATE async_task SET status = ?, phase = ?, message = ?, updated_at = ? WHERE id = ?",
                TaskStatus.RUNNING.name(),
                phase.name(),
                message,
                TimeUtil.text(LocalDateTime.now()),
                id);
    }

    public void complete(String id, Long articleId, String message) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE async_task SET status = ?, phase = ?, message = ?, article_id = ?,"
                        + " error_message = NULL, updated_at = ?, ended_at = ? WHERE id = ?",
                TaskStatus.COMPLETED.name(),
                TaskPhase.COMPLETED.name(),
                message,
                articleId,
                TimeUtil.text(now),
                TimeUtil.text(now),
                id);
    }

    public void fail(String id, String message) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE async_task SET status = ?, phase = ?, message = ?, error_message = ?,"
                        + " updated_at = ?, ended_at = ? WHERE id = ?",
                TaskStatus.FAILED.name(),
                TaskPhase.FAILED.name(),
                message,
                message,
                TimeUtil.text(now),
                TimeUtil.text(now),
                id);
    }
}
