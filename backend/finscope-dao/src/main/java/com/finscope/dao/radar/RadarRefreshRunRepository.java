package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RadarRefreshRunRepository {
    private final JdbcTemplate jdbc;

    public RadarRefreshRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RadarRefreshRun startRun(String runKey, String triggerType, LocalDateTime startedAt) {
        jdbc.update("INSERT INTO radar_refresh_run(run_key,trigger_type,status,started_at) VALUES(?,?,?,?)",
                runKey, triggerType, "RUNNING", TimeUtil.text(startedAt));
        return findByKey(runKey).orElseThrow(IllegalStateException::new);
    }

    public RadarRefreshStep startStep(Long runId, String stepCode, LocalDateTime startedAt) {
        jdbc.update("INSERT INTO radar_refresh_step(run_id,step_code,status,started_at) VALUES(?,?,?,?)",
                runId, stepCode, "RUNNING", TimeUtil.text(startedAt));
        return findStep(runId, stepCode).orElseThrow(IllegalStateException::new);
    }

    public RadarRefreshStep completeStep(Long runId, String stepCode, String status,
                                         int inputCount, int outputCount, String details, LocalDateTime completedAt) {
        jdbc.update("UPDATE radar_refresh_step SET status=?,completed_at=?,input_count=?,output_count=?,details=? "
                        + "WHERE run_id=? AND step_code=?",
                status, TimeUtil.text(completedAt), inputCount, outputCount, details, runId, stepCode);
        return findStep(runId, stepCode).orElseThrow(IllegalStateException::new);
    }

    public RadarRefreshRun completeRun(Long id, int sourceCount, int signalCount, int eventCount,
                                       String warning, LocalDateTime completedAt) {
        jdbc.update("UPDATE radar_refresh_run SET status='SUCCESS',completed_at=?,source_count=?,signal_count=?,"
                        + "event_count=?,warning=?,error=NULL WHERE id=?",
                TimeUtil.text(completedAt), sourceCount, signalCount, eventCount, emptyToNull(warning), id);
        return findById(id).orElseThrow(IllegalStateException::new);
    }

    public RadarRefreshRun failRun(Long id, String error, LocalDateTime completedAt) {
        jdbc.update("UPDATE radar_refresh_run SET status='FAILED',completed_at=?,error=? WHERE id=?",
                TimeUtil.text(completedAt), error, id);
        return findById(id).orElseThrow(IllegalStateException::new);
    }

    public Optional<RadarRefreshRun> findLatestCompletedRun() {
        List<RadarRefreshRun> values = jdbc.query("SELECT * FROM radar_refresh_run WHERE status='SUCCESS' "
                        + "ORDER BY completed_at DESC,id DESC LIMIT 1", runMapper());
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<RadarRefreshStep> findSteps(Long runId) {
        return jdbc.query("SELECT * FROM radar_refresh_step WHERE run_id=? ORDER BY id", stepMapper(), runId);
    }

    private Optional<RadarRefreshRun> findByKey(String key) {
        List<RadarRefreshRun> values = jdbc.query("SELECT * FROM radar_refresh_run WHERE run_key=?", runMapper(), key);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private Optional<RadarRefreshRun> findById(Long id) {
        List<RadarRefreshRun> values = jdbc.query("SELECT * FROM radar_refresh_run WHERE id=?", runMapper(), id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private Optional<RadarRefreshStep> findStep(Long runId, String stepCode) {
        List<RadarRefreshStep> values = jdbc.query("SELECT * FROM radar_refresh_step WHERE run_id=? AND step_code=?",
                stepMapper(), runId, stepCode);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private org.springframework.jdbc.core.RowMapper<RadarRefreshRun> runMapper() {
        return (rs, rowNum) -> {
            RadarRefreshRun value = new RadarRefreshRun();
            value.setId(rs.getLong("id")); value.setRunKey(rs.getString("run_key"));
            value.setTriggerType(rs.getString("trigger_type")); value.setStatus(rs.getString("status"));
            value.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
            value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at"));
            value.setSourceCount(rs.getInt("source_count")); value.setSignalCount(rs.getInt("signal_count"));
            value.setEventCount(rs.getInt("event_count")); value.setWarning(rs.getString("warning"));
            value.setError(rs.getString("error")); return value;
        };
    }

    private org.springframework.jdbc.core.RowMapper<RadarRefreshStep> stepMapper() {
        return (rs, rowNum) -> {
            RadarRefreshStep value = new RadarRefreshStep(); value.setId(rs.getLong("id"));
            value.setRunId(rs.getLong("run_id")); value.setStepCode(rs.getString("step_code"));
            value.setStatus(rs.getString("status")); value.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
            value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at")); value.setInputCount(rs.getInt("input_count"));
            value.setOutputCount(rs.getInt("output_count")); value.setDetails(rs.getString("details"));
            value.setError(rs.getString("error")); return value;
        };
    }

    private String emptyToNull(String value) { return value == null || value.trim().isEmpty() ? null : value; }
}
