package com.finscope.dao.attribution;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.attribution.AttributionResearchRun;
import com.finscope.domain.attribution.AttributionResearchStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AttributionResearchRunRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public AttributionResearchRunRepository() {
    }

    public AttributionResearchRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AttributionResearchRun> runMapper = (rs, rowNum) -> {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setId(rs.getLong("id"));
        run.setReportId(rs.getLong("report_id"));
        run.setStatus(rs.getString("status"));
        run.setPlanJson(rs.getString("plan_json"));
        run.setBudgetJson(rs.getString("budget_json"));
        run.setCurrentStep(rs.getString("current_step"));
        run.setTerminationReason(rs.getString("termination_reason"));
        run.setErrorMessage(rs.getString("error_message"));
        run.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        run.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return run;
    };

    private final RowMapper<AttributionResearchStep> stepMapper = (rs, rowNum) -> {
        AttributionResearchStep step = new AttributionResearchStep();
        step.setId(rs.getLong("id"));
        step.setRunId(rs.getLong("run_id"));
        step.setStepId(rs.getString("step_id"));
        step.setTrack(rs.getString("track"));
        step.setStatus(rs.getString("status"));
        step.setInputSummary(rs.getString("input_summary"));
        step.setOutputSummary(rs.getString("output_summary"));
        step.setAttempt(rs.getInt("attempt"));
        step.setMaxAttempts(rs.getInt("max_attempts"));
        step.setErrorMessage(rs.getString("error_message"));
        step.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        step.setEndedAt(TimeUtil.localDateTime(rs, "ended_at"));
        return step;
    };

    public AttributionResearchRun createRun(AttributionResearchRun run) {
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO attribution_research_run(report_id,status,plan_json,budget_json,current_step,termination_reason,error_message,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, run.getReportId());
            ps.setString(2, run.getStatus());
            ps.setString(3, run.getPlanJson());
            ps.setString(4, run.getBudgetJson());
            ps.setString(5, run.getCurrentStep());
            ps.setString(6, run.getTerminationReason());
            ps.setString(7, run.getErrorMessage());
            ps.setString(8, TimeUtil.text(now));
            ps.setString(9, TimeUtil.text(now));
            return ps;
        }, keys);
        run.setId(keys.getKey().longValue());
        return run;
    }

    public Optional<AttributionResearchRun> findByReportId(Long reportId) {
        List<AttributionResearchRun> runs = jdbcTemplate.query(
                "SELECT * FROM attribution_research_run WHERE report_id = ?", runMapper, reportId);
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }

    public void updateRun(Long id, String status, String errorMessage, String terminationReason) {
        jdbcTemplate.update("UPDATE attribution_research_run SET status=?, error_message=?, termination_reason=?, updated_at=? WHERE id=?",
                status, errorMessage, terminationReason, TimeUtil.text(LocalDateTime.now()), id);
    }

    /** 运行中只推进阶段，不应覆盖最终状态或终止原因。 */
    public void updateCurrentStep(Long id, String currentStep) {
        jdbcTemplate.update("UPDATE attribution_research_run SET current_step=?, updated_at=? WHERE id=?",
                currentStep, TimeUtil.text(LocalDateTime.now()), id);
    }

    /** 将进程异常退出后长期停留在 RUNNING 的运行收敛为失败，供启动恢复使用。 */
    public int failStaleRunningRuns(LocalDateTime cutoff) {
        return jdbcTemplate.update("UPDATE attribution_research_run SET status='FAILED', error_message=?, "
                        + "termination_reason='PROCESS_RESTART', updated_at=? WHERE status='RUNNING' AND updated_at<?",
                "服务重启导致研究中断，请重新发起归因", TimeUtil.text(LocalDateTime.now()), TimeUtil.text(cutoff));
    }

    public AttributionResearchStep saveStep(AttributionResearchStep step) {
        jdbcTemplate.update("INSERT INTO attribution_research_step(run_id,step_id,track,status,input_summary,output_summary,attempt,max_attempts,error_message,started_at,ended_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(run_id,step_id) DO UPDATE SET track=excluded.track,status=excluded.status,input_summary=excluded.input_summary,output_summary=excluded.output_summary,attempt=excluded.attempt,max_attempts=excluded.max_attempts,error_message=excluded.error_message,started_at=COALESCE(excluded.started_at,attribution_research_step.started_at),ended_at=excluded.ended_at",
                step.getRunId(), step.getStepId(), step.getTrack(), step.getStatus(), step.getInputSummary(),
                step.getOutputSummary(), value(step.getAttempt()), value(step.getMaxAttempts()), step.getErrorMessage(),
                TimeUtil.text(step.getStartedAt()), TimeUtil.text(step.getEndedAt()));
        return step;
    }

    public List<AttributionResearchStep> findStepsByRunId(Long runId) {
        return jdbcTemplate.query("SELECT * FROM attribution_research_step WHERE run_id=? ORDER BY step_id ASC", stepMapper, runId);
    }

    /** 删除报告对应的运行与所有轨道步骤。 */
    public void deleteByReportId(Long reportId) {
        jdbcTemplate.update("DELETE FROM attribution_research_step WHERE run_id IN "
                + "(SELECT id FROM attribution_research_run WHERE report_id = ?)", reportId);
        jdbcTemplate.update("DELETE FROM attribution_research_run WHERE report_id = ?", reportId);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
