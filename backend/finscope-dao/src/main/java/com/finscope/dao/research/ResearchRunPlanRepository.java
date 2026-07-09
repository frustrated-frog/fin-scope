package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchRunPlanStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class ResearchRunPlanRepository {
    private final JdbcTemplate jdbcTemplate;

    public ResearchRunPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ResearchRunPlanStep> mapper = (rs, rowNum) -> {
        ResearchRunPlanStep step = new ResearchRunPlanStep();
        step.setId(rs.getLong("id"));
        step.setResearchRunId(rs.getLong("research_run_id"));
        step.setStepId(rs.getString("step_id"));
        step.setTitle(rs.getString("title"));
        step.setStepType(rs.getString("step_type"));
        step.setExecutor(rs.getString("executor"));
        step.setStatus(rs.getString("status"));
        step.setDependencies(parseList(rs.getString("dependencies")));
        step.setInputSummary(rs.getString("input_summary"));
        step.setOutputSummary(rs.getString("output_summary"));
        step.setErrorType(rs.getString("error_type"));
        step.setErrorMessage(rs.getString("error_message"));
        step.setFallbackUsed(rs.getInt("fallback_used") == 1);
        step.setFallbackReason(rs.getString("fallback_reason"));
        step.setTerminationReason(rs.getString("termination_reason"));
        step.setAttempt(rs.getInt("attempt"));
        step.setMaxAttempts(rs.getInt("max_attempts"));
        step.setProgressDelta(rs.getInt("progress_delta"));
        step.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        step.setEndedAt(TimeUtil.localDateTime(rs, "ended_at"));
        step.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        step.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        step.setMetadataJson(rs.getString("metadata_json"));
        return step;
    };

    public List<ResearchRunPlanStep> replaceForRun(Long researchRunId, List<ResearchRunPlanStep> steps) {
        jdbcTemplate.update("DELETE FROM research_run_plan WHERE research_run_id = ?", researchRunId);
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        for (ResearchRunPlanStep step : steps) {
            insert(step);
        }
        return findByRunId(researchRunId);
    }

    public ResearchRunPlanStep update(ResearchRunPlanStep step) {
        step.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE research_run_plan SET status=?, input_summary=?, output_summary=?, error_type=?, "
                        + "error_message=?, fallback_used=?, fallback_reason=?, termination_reason=?, attempt=?, "
                        + "max_attempts=?, progress_delta=?, started_at=?, ended_at=?, updated_at=?, metadata_json=? "
                        + "WHERE research_run_id=? AND step_id=?",
                step.getStatus(), step.getInputSummary(), step.getOutputSummary(), step.getErrorType(),
                step.getErrorMessage(), step.isFallbackUsed() ? 1 : 0, step.getFallbackReason(),
                step.getTerminationReason(), step.getAttempt(), step.getMaxAttempts(), step.getProgressDelta(),
                TimeUtil.text(step.getStartedAt()), TimeUtil.text(step.getEndedAt()), TimeUtil.text(step.getUpdatedAt()),
                step.getMetadataJson(), step.getResearchRunId(), step.getStepId());
        return findByRunIdAndStepId(step.getResearchRunId(), step.getStepId());
    }

    public int recoverOpenStepsForInterruptedRuns(String errorMessage) {
        LocalDateTime endedAt = LocalDateTime.now();
        return jdbcTemplate.update("UPDATE research_run_plan SET "
                        + "status = CASE WHEN status = 'RUNNING' THEN 'FAILED' ELSE 'SKIPPED' END, "
                        + "error_type = CASE WHEN status = 'RUNNING' THEN 'INTERRUPTED' ELSE error_type END, "
                        + "error_message = CASE WHEN status = 'RUNNING' THEN ? ELSE error_message END, "
                        + "termination_reason = 'PROCESS_RESTART', ended_at = ?, updated_at = ? "
                        + "WHERE research_run_id IN (SELECT id FROM research_run WHERE status = 'RUNNING') "
                        + "AND status IN ('RUNNING', 'PENDING')",
                errorMessage, TimeUtil.text(endedAt), TimeUtil.text(endedAt));
    }

    public List<ResearchRunPlanStep> findByRunId(Long researchRunId) {
        return jdbcTemplate.query("SELECT * FROM research_run_plan WHERE research_run_id = ? ORDER BY id ASC",
                mapper, researchRunId);
    }

    public ResearchRunPlanStep findByRunIdAndStepId(Long researchRunId, String stepId) {
        List<ResearchRunPlanStep> steps = jdbcTemplate.query(
                "SELECT * FROM research_run_plan WHERE research_run_id = ? AND step_id = ?",
                mapper, researchRunId, stepId);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Research run plan step not found: " + researchRunId + "/" + stepId);
        }
        return steps.get(0);
    }

    private void insert(ResearchRunPlanStep step) {
        LocalDateTime now = LocalDateTime.now();
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        jdbcTemplate.update("INSERT INTO research_run_plan(research_run_id,step_id,title,step_type,executor,status,"
                        + "dependencies,input_summary,output_summary,error_type,error_message,fallback_used,"
                        + "fallback_reason,termination_reason,attempt,max_attempts,progress_delta,started_at,ended_at,"
                        + "created_at,updated_at,metadata_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                step.getResearchRunId(), step.getStepId(), step.getTitle(), step.getStepType(), step.getExecutor(),
                step.getStatus(), joinList(step.getDependencies()), step.getInputSummary(), step.getOutputSummary(),
                step.getErrorType(), step.getErrorMessage(), step.isFallbackUsed() ? 1 : 0, step.getFallbackReason(),
                step.getTerminationReason(), step.getAttempt(), step.getMaxAttempts(), step.getProgressDelta(),
                TimeUtil.text(step.getStartedAt()), TimeUtil.text(step.getEndedAt()), TimeUtil.text(step.getCreatedAt()),
                TimeUtil.text(step.getUpdatedAt()), step.getMetadataJson());
    }

    private static String joinList(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(",", values);
    }

    private static List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
