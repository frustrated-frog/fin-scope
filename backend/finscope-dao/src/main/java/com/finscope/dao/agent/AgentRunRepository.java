package com.finscope.dao.agent;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.agent.AgentRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class AgentRunRepository {
    private final JdbcTemplate jdbcTemplate;

    public AgentRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AgentRun> mapper = (rs, rowNum) -> {
        AgentRun run = new AgentRun();
        run.setId(rs.getLong("id"));
        run.setResearchRunId(readLong(rs, "research_run_id"));
        run.setEventId(readLong(rs, "event_id"));
        run.setArticleId(readLong(rs, "article_id"));
        run.setNodeName(rs.getString("node_name"));
        run.setStatus(rs.getString("status"));
        run.setInput(rs.getString("input"));
        run.setOutput(rs.getString("output"));
        run.setErrorMessage(rs.getString("error_message"));
        run.setDurationMs(rs.getLong("duration_ms"));
        run.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        run.setStepId(rs.getString("step_id"));
        run.setAttempt(rs.getInt("attempt"));
        run.setActionFingerprint(rs.getString("action_fingerprint"));
        run.setInputHash(rs.getString("input_hash"));
        run.setOutputHash(rs.getString("output_hash"));
        run.setErrorType(rs.getString("error_type"));
        run.setFallbackUsed(rs.getInt("fallback_used") == 1);
        run.setFallbackReason(rs.getString("fallback_reason"));
        run.setTerminationReason(rs.getString("termination_reason"));
        run.setProgressDelta(rs.getInt("progress_delta"));
        run.setBudgetSnapshot(rs.getString("budget_snapshot"));
        run.setMetadataJson(rs.getString("metadata_json"));
        return run;
    };

    public void record(String nodeName, String status, String input, String output, String errorMessage, long durationMs) {
        record(null, null, null, nodeName, status, input, output, errorMessage, durationMs);
    }

    public void record(Long researchRunId,
                       Long eventId,
                       Long articleId,
                       String nodeName,
                       String status,
                       String input,
                       String output,
                       String errorMessage,
                       long durationMs) {
        jdbcTemplate.update("INSERT INTO agent_run(research_run_id,event_id,article_id,node_name,status,input,output,"
                        + "error_message,duration_ms,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                researchRunId, eventId, articleId, nodeName, status, input, output, errorMessage,
                durationMs, TimeUtil.text(nodeStartTime(durationMs)));
    }

    public void record(AgentRun run) {
        LocalDateTime createdAt = run.getCreatedAt() == null
                ? nodeStartTime(run.getDurationMs())
                : run.getCreatedAt();
        jdbcTemplate.update("INSERT INTO agent_run(research_run_id,event_id,article_id,node_name,status,input,output,"
                        + "error_message,duration_ms,created_at,step_id,attempt,action_fingerprint,input_hash,"
                        + "output_hash,error_type,fallback_used,fallback_reason,termination_reason,progress_delta,"
                        + "budget_snapshot,metadata_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                run.getResearchRunId(), run.getEventId(), run.getArticleId(), run.getNodeName(), run.getStatus(),
                run.getInput(), run.getOutput(), run.getErrorMessage(), run.getDurationMs(),
                TimeUtil.text(createdAt), run.getStepId(), run.getAttempt(), run.getActionFingerprint(),
                run.getInputHash(), run.getOutputHash(), run.getErrorType(), run.isFallbackUsed() ? 1 : 0,
                run.getFallbackReason(), run.getTerminationReason(), run.getProgressDelta(),
                run.getBudgetSnapshot(), run.getMetadataJson());
    }

    public List<AgentRun> latest(int limit) {
        return jdbcTemplate.query("SELECT * FROM agent_run ORDER BY id DESC LIMIT ?", mapper, limit);
    }

    public List<AgentRun> findByResearchRunId(Long researchRunId) {
        return jdbcTemplate.query("SELECT * FROM agent_run WHERE research_run_id = ? ORDER BY id ASC",
                mapper, researchRunId);
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nodeStartTime(long durationMs) {
        return LocalDateTime.now().minus(Math.max(0L, durationMs), ChronoUnit.MILLIS);
    }
}
