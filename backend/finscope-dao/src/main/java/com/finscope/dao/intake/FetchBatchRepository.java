package com.finscope.dao.intake;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.intake.IntakeEnums;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FetchBatchRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<FetchBatch> mapper = (rs, rowNum) -> {
        FetchBatch batch = new FetchBatch();
        batch.setId(rs.getLong("id"));
        batch.setSourceId(readLong(rs, "source_id"));
        batch.setSourceName(rs.getString("source_name"));
        batch.setTriggerType(rs.getString("trigger_type"));
        batch.setStatus(rs.getString("status"));
        batch.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        batch.setEndedAt(TimeUtil.localDateTime(rs, "ended_at"));
        batch.setLookbackDays(rs.getInt("lookback_days"));
        batch.setMaxItemsRequested(rs.getInt("max_items_requested"));
        batch.setRawItemCount(rs.getInt("raw_item_count"));
        batch.setCandidateCount(rs.getInt("candidate_count"));
        batch.setAgentReviewedCount(rs.getInt("agent_reviewed_count"));
        batch.setDuplicateCount(rs.getInt("duplicate_count"));
        batch.setLowValueCount(rs.getInt("low_value_count"));
        batch.setErrorMessage(rs.getString("error_message"));
        batch.setBatchSummaryJson(rs.getString("batch_summary_json"));
        batch.setBatchSummaryText(rs.getString("batch_summary_text"));
        batch.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        batch.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return batch;
    };

    public FetchBatch start(FetchBatch batch) {
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus(IntakeEnums.BATCH_RUNNING);
        batch.setStartedAt(now);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO fetch_batch(source_id,source_name,trigger_type,status,started_at,lookback_days,"
                            + "max_items_requested,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            setLong(ps, 1, batch.getSourceId());
            ps.setString(2, batch.getSourceName());
            ps.setString(3, blankDefault(batch.getTriggerType(), IntakeEnums.TRIGGER_MANUAL));
            ps.setString(4, batch.getStatus());
            ps.setString(5, TimeUtil.text(batch.getStartedAt()));
            ps.setInt(6, batch.getLookbackDays() <= 0 ? 3 : batch.getLookbackDays());
            ps.setInt(7, batch.getMaxItemsRequested() <= 0 ? 10 : batch.getMaxItemsRequested());
            ps.setString(8, TimeUtil.text(batch.getCreatedAt()));
            ps.setString(9, TimeUtil.text(batch.getUpdatedAt()));
            return ps;
        }, keyHolder);
        batch.setId(keyHolder.getKey().longValue());
        return findById(batch.getId()).orElse(batch);
    }

    public FetchBatch finish(FetchBatch batch,
                             String status,
                             int rawItemCount,
                             int candidateCount,
                             int agentReviewedCount,
                             int duplicateCount,
                             int lowValueCount,
                             String errorMessage,
                             String batchSummaryJson,
                             String batchSummaryText) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE fetch_batch SET status=?, ended_at=?, raw_item_count=?, candidate_count=?, "
                        + "agent_reviewed_count=?, duplicate_count=?, low_value_count=?, error_message=?, "
                        + "batch_summary_json=?, batch_summary_text=?, updated_at=? WHERE id=?",
                status, TimeUtil.text(now), rawItemCount, candidateCount, agentReviewedCount, duplicateCount,
                lowValueCount, errorMessage, batchSummaryJson, batchSummaryText, TimeUtil.text(now), batch.getId());
        return findById(batch.getId()).orElse(batch);
    }

    public Optional<FetchBatch> findById(Long id) {
        List<FetchBatch> batches = jdbcTemplate.query("SELECT * FROM fetch_batch WHERE id = ?", mapper, id);
        return batches.isEmpty() ? Optional.empty() : Optional.of(batches.get(0));
    }

    public List<FetchBatch> latest(int limit) {
        return jdbcTemplate.query("SELECT * FROM fetch_batch ORDER BY id DESC LIMIT ?", mapper, limit);
    }

    public List<FetchBatch> findBySourceId(Long sourceId, int limit) {
        return jdbcTemplate.query("SELECT * FROM fetch_batch WHERE source_id = ? ORDER BY id DESC LIMIT ?",
                mapper, sourceId, limit);
    }

    public boolean hasScheduledRunForSlot(Long sourceId, LocalDate date, String slot) {
        String start = date.atStartOfDay().toString();
        String end = date.plusDays(1).atStartOfDay().toString();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fetch_batch WHERE source_id = ? AND trigger_type = ? "
                        + "AND started_at >= ? AND started_at < ? AND batch_summary_json LIKE ?",
                Integer.class, sourceId, IntakeEnums.TRIGGER_SCHEDULED, start, end, "%\"slot\":\"" + slot + "\"%");
        return count != null && count > 0;
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
