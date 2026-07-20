package com.finscope.dao.quant;

import com.finscope.domain.quant.data.QuantDataSyncRun;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
public class QuantDataSyncRunRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<QuantDataSyncRun> mapper = (rs, row) -> new QuantDataSyncRun(
            rs.getLong("id"), rs.getLong("dataset_id"), rs.getString("trigger_type"),
            rs.getString("status"), rs.getInt("requested_instruments"),
            rs.getInt("succeeded_instruments"), rs.getInt("failed_instruments"),
            rs.getInt("inserted_rows"), rs.getInt("degraded_instruments"),
            rs.getString("source_summary"), rs.getString("warning_summary"),
            LocalDateTime.parse(rs.getString("started_at")),
            rs.getString("finished_at") == null ? null : LocalDateTime.parse(rs.getString("finished_at")));

    public QuantDataSyncRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public QuantDataSyncRun start(Long datasetId, String triggerType, int requested,
                                  LocalDateTime startedAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO quant_data_sync_run(dataset_id,trigger_type,status,"
                                + "requested_instruments,started_at) VALUES(?,?,'RUNNING',?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, datasetId);
                statement.setString(2, triggerType);
                statement.setInt(3, requested);
                statement.setString(4, startedAt.toString());
                return statement;
            }, keys);
        } catch (DataAccessException error) {
            if (findRunning(datasetId).isPresent()) {
                throw new DataIntegrityViolationException(
                        "dataset already has an active market-data sync", error);
            }
            throw error;
        }
        if (keys.getKey() == null) throw new IllegalStateException("quant data sync run id was not generated");
        return find(keys.getKey().longValue()).orElseThrow(() ->
                new IllegalStateException("created quant data sync run cannot be loaded"));
    }

    public QuantDataSyncRun finish(Long id, String status, int succeeded, int failed,
                                   int insertedRows, int degraded, String sourceSummary,
                                   String warningSummary, LocalDateTime finishedAt) {
        int updated = jdbc.update("UPDATE quant_data_sync_run SET status=?,succeeded_instruments=?,"
                        + "failed_instruments=?,inserted_rows=?,degraded_instruments=?,source_summary=?,"
                        + "warning_summary=?,finished_at=? WHERE id=? AND status='RUNNING'",
                status, succeeded, failed, insertedRows, degraded, sourceSummary,
                warningSummary, finishedAt.toString(), id);
        if (updated != 1) throw new IllegalArgumentException("active quant data sync run does not exist: " + id);
        return find(id).orElseThrow(() -> new IllegalStateException("finished quant data sync run cannot be loaded"));
    }

    public List<QuantDataSyncRun> findByDatasetId(Long datasetId) {
        return jdbc.query("SELECT * FROM quant_data_sync_run WHERE dataset_id=? ORDER BY id DESC", mapper, datasetId);
    }

    private Optional<QuantDataSyncRun> find(Long id) {
        return jdbc.query("SELECT * FROM quant_data_sync_run WHERE id=?", mapper, id).stream().findFirst();
    }

    private Optional<QuantDataSyncRun> findRunning(Long datasetId) {
        return jdbc.query("SELECT * FROM quant_data_sync_run WHERE dataset_id=? AND status='RUNNING' LIMIT 1",
                mapper, datasetId).stream().findFirst();
    }
}
