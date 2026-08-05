package com.finscope.dao.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeProjectionJobRepository {
    private static final int LEASE_MINUTES = 10;
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<KnowledgeProjectionJob> mapper = (resultSet, rowNumber) -> {
        KnowledgeProjectionJob job = new KnowledgeProjectionJob();
        job.setId(resultSet.getLong("id"));
        job.setTopicId(resultSet.getLong("topic_id"));
        job.setEntryId(nullableLong(resultSet, "entry_id"));
        job.setStatus(resultSet.getString("status"));
        job.setAttemptCount(resultSet.getInt("attempt_count"));
        job.setLastError(resultSet.getString("last_error"));
        job.setCreatedAt(TimeUtil.localDateTime(resultSet, "created_at"));
        job.setUpdatedAt(TimeUtil.localDateTime(resultSet, "updated_at"));
        return job;
    };

    public KnowledgeProjectionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KnowledgeProjectionJob enqueue(Long topicId, Long entryId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT OR IGNORE INTO knowledge_projection_job(" +
                        "topic_id,entry_id,status,attempt_count,created_at,updated_at) " +
                        "VALUES(?,?,'PENDING',0,?,?)",
                topicId, entryId, TimeUtil.text(now), TimeUtil.text(now)
        );
        return findByEntry(topicId, entryId)
                .orElseThrow(() -> new IllegalStateException("Unable to enqueue projection job"));
    }

    public Optional<KnowledgeProjectionJob> findById(Long id) {
        List<KnowledgeProjectionJob> jobs = jdbcTemplate.query(
                "SELECT * FROM knowledge_projection_job WHERE id=?", mapper, id);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.get(0));
    }

    public Optional<KnowledgeProjectionJob> findByEntry(Long topicId, Long entryId) {
        List<KnowledgeProjectionJob> jobs = jdbcTemplate.query(
                "SELECT * FROM knowledge_projection_job WHERE topic_id=? AND entry_id=?",
                mapper, topicId, entryId);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.get(0));
    }

    public List<KnowledgeProjectionJob> findRecoverable(int limit) {
        if (limit < 1 || limit > 200) {
            throw new BusinessException(BizErrorCode.PROJECTION_BATCH_SIZE_INVALID);
        }
        String leaseExpiredBefore = TimeUtil.text(LocalDateTime.now().minusMinutes(LEASE_MINUTES));
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_projection_job WHERE status IN ('PENDING','FAILED') " +
                        "OR (status='RUNNING' AND updated_at<?) " +
                        "ORDER BY updated_at ASC,id ASC LIMIT ?",
                mapper, leaseExpiredBefore, limit
        );
    }

    public boolean claim(Long id) {
        String now = TimeUtil.text(LocalDateTime.now());
        String leaseExpiredBefore = TimeUtil.text(LocalDateTime.now().minusMinutes(LEASE_MINUTES));
        return jdbcTemplate.update(
                "UPDATE knowledge_projection_job SET status='RUNNING'," +
                        "attempt_count=attempt_count+1,updated_at=? " +
                        "WHERE id=? AND (status IN ('PENDING','FAILED') " +
                        "OR (status='RUNNING' AND updated_at<?))",
                now, id, leaseExpiredBefore
        ) == 1;
    }

    public void markFailed(Long id, String errorSummary) {
        jdbcTemplate.update(
                "UPDATE knowledge_projection_job SET status='FAILED',last_error=?,updated_at=? " +
                        "WHERE id=? AND status='RUNNING'",
                errorSummary, TimeUtil.text(LocalDateTime.now()), id
        );
    }

    public void markCompleted(Long id) {
        jdbcTemplate.update(
                "UPDATE knowledge_projection_job SET status='COMPLETED',updated_at=? " +
                        "WHERE id=? AND status='RUNNING'",
                TimeUtil.text(LocalDateTime.now()), id
        );
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
