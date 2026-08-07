package com.finscope.dao.research.agent;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import com.finscope.domain.research.agent.ResearchToolObservation;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.finscope.common.exception.BizErrorCode;

@Repository
public class ResearchAgentRepository {
    private static final String LIST_SEPARATOR = "\u001F";

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ResearchAgentState> stateMapper = (rs, rowNum) -> {
        ResearchAgentState value = new ResearchAgentState();
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setStatus(rs.getString("status"));
        value.setStateVersion(rs.getInt("state_version"));
        value.setCurrentSubgoal(rs.getString("current_subgoal"));
        value.setPlanSummary(rs.getString("plan_summary"));
        value.setMemorySummary(rs.getString("memory_summary"));
        value.setEvidenceSummary(rs.getString("evidence_summary"));
        value.setAttemptedFingerprints(parseList(rs.getString("attempted_fingerprints")));
        long observationId = rs.getLong("last_observation_id");
        value.setLastObservationId(rs.wasNull() ? null : observationId);
        value.setDecisionCount(rs.getInt("decision_count"));
        value.setReplanCount(rs.getInt("replan_count"));
        value.setNoProgressCount(rs.getInt("no_progress_count"));
        value.setFinishRejectionCount(rs.getInt("finish_rejection_count"));
        value.setFallbackCount(rs.getInt("fallback_count"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<ResearchAgentDecision> decisionMapper = (rs, rowNum) -> {
        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setId(rs.getLong("id"));
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setIteration(rs.getInt("iteration"));
        value.setDecisionType(rs.getString("decision_type"));
        value.setCurrentSubgoal(rs.getString("current_subgoal"));
        value.setMissionTaskKey(rs.getString("mission_task_key"));
        value.setToolCode(rs.getString("tool_code"));
        value.setArgumentsJson(rs.getString("arguments_json"));
        value.setTargetGap(rs.getString("target_gap"));
        value.setExpectedObservation(rs.getString("expected_observation"));
        value.setDecisionSummary(rs.getString("decision_summary"));
        value.setConfidence(rs.getDouble("confidence"));
        value.setDecisionMode(rs.getString("decision_mode"));
        value.setActionFingerprint(rs.getString("action_fingerprint"));
        value.setStatus(rs.getString("status"));
        value.setValidationError(rs.getString("validation_error"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<ResearchToolObservation> observationMapper = (rs, rowNum) -> {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setId(rs.getLong("id"));
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setDecisionId(rs.getLong("decision_id"));
        value.setToolCode(rs.getString("tool_code"));
        value.setStatus(rs.getString("status"));
        value.setObservationSummary(rs.getString("observation_summary"));
        value.setNewInformation(rs.getString("new_information"));
        value.setEvidenceDelta(rs.getInt("evidence_delta"));
        value.setSourceDelta(rs.getInt("source_delta"));
        value.setDataRefs(parseList(rs.getString("data_refs")));
        value.setErrorType(rs.getString("error_type"));
        value.setRetryable(rs.getInt("retryable") == 1);
        value.setAttemptCount(rs.getInt("attempt_count"));
        value.setStateHash(rs.getString("state_hash"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public ResearchAgentState initialize(Long runId, String planSummary) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO research_agent_state(research_run_id,status,state_version,plan_summary,"
                        + "decision_count,replan_count,no_progress_count,finish_rejection_count,fallback_count,"
                        + "created_at,updated_at) VALUES(?,'READY',0,?,0,0,0,0,0,?,?) "
                        + "ON CONFLICT(research_run_id) DO NOTHING",
                runId, planSummary, TimeUtil.text(now), TimeUtil.text(now));
        return findState(runId).orElseThrow(
                () -> new IllegalStateException("Research agent state initialization failed: " + runId));
    }

    public boolean updateState(ResearchAgentState state, int expectedVersion) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE research_agent_state SET status=?,state_version=state_version+1,"
                        + "current_subgoal=?,plan_summary=?,memory_summary=?,evidence_summary=?,"
                        + "attempted_fingerprints=?,last_observation_id=?,decision_count=?,replan_count=?,"
                        + "no_progress_count=?,finish_rejection_count=?,fallback_count=?,updated_at=? "
                        + "WHERE research_run_id=? AND state_version=?",
                state.getStatus(), state.getCurrentSubgoal(), state.getPlanSummary(), state.getMemorySummary(),
                state.getEvidenceSummary(), joinList(state.getAttemptedFingerprints()), state.getLastObservationId(),
                state.getDecisionCount(), state.getReplanCount(), state.getNoProgressCount(),
                state.getFinishRejectionCount(), state.getFallbackCount(), TimeUtil.text(now),
                state.getResearchRunId(), expectedVersion);
        if (updated == 1) {
            state.setStateVersion(expectedVersion + 1);
            state.setUpdatedAt(now);
        }
        return updated == 1;
    }

    public ResearchAgentDecision appendDecision(ResearchAgentDecision decision) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO research_agent_decision(research_run_id,iteration,decision_type,current_subgoal,"
                            + "mission_task_key,tool_code,arguments_json,target_gap,expected_observation,decision_summary,confidence,"
                            + "decision_mode,action_fingerprint,status,validation_error,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, decision.getResearchRunId());
            statement.setInt(2, decision.getIteration());
            statement.setString(3, decision.getDecisionType());
            statement.setString(4, decision.getCurrentSubgoal());
            statement.setString(5, decision.getMissionTaskKey());
            statement.setString(6, decision.getToolCode());
            statement.setString(7, decision.getArgumentsJson());
            statement.setString(8, decision.getTargetGap());
            statement.setString(9, decision.getExpectedObservation());
            statement.setString(10, decision.getDecisionSummary());
            statement.setDouble(11, decision.getConfidence());
            statement.setString(12, decision.getDecisionMode());
            statement.setString(13, decision.getActionFingerprint());
            statement.setString(14, decision.getStatus());
            statement.setString(15, decision.getValidationError());
            statement.setString(16, TimeUtil.text(now));
            statement.setString(17, TimeUtil.text(now));
            return statement;
        }, keys);
        decision.setId(requiredKey(keys));
        decision.setCreatedAt(now);
        decision.setUpdatedAt(now);
        return decision;
    }

    public ResearchToolObservation appendObservation(ResearchToolObservation observation) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO research_tool_observation(research_run_id,decision_id,tool_code,status,"
                            + "observation_summary,new_information,evidence_delta,source_delta,data_refs,error_type,"
                            + "retryable,attempt_count,state_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, observation.getResearchRunId());
            statement.setLong(2, observation.getDecisionId());
            statement.setString(3, observation.getToolCode());
            statement.setString(4, observation.getStatus());
            statement.setString(5, observation.getObservationSummary());
            statement.setString(6, observation.getNewInformation());
            statement.setInt(7, observation.getEvidenceDelta());
            statement.setInt(8, observation.getSourceDelta());
            statement.setString(9, joinList(observation.getDataRefs()));
            statement.setString(10, observation.getErrorType());
            statement.setInt(11, observation.isRetryable() ? 1 : 0);
            statement.setInt(12, observation.getAttemptCount());
            statement.setString(13, observation.getStateHash());
            statement.setString(14, TimeUtil.text(now));
            return statement;
        }, keys);
        observation.setId(requiredKey(keys));
        observation.setCreatedAt(now);
        return observation;
    }

    public boolean updateDecisionStatus(Long decisionId, String status, String validationError) {
        return jdbcTemplate.update("UPDATE research_agent_decision SET status=?,validation_error=?,updated_at=? "
                        + "WHERE id=?",
                status, validationError, TimeUtil.text(LocalDateTime.now()), decisionId) == 1;
    }

    public int interruptRunning(String message) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update("UPDATE research_agent_state SET status='INTERRUPTED',"
                        + "state_version=state_version+1,memory_summary=?,updated_at=? "
                        + "WHERE status IN ('READY','DECIDING','EXECUTING','REPLANNING','VERIFYING')",
                message, TimeUtil.text(now));
    }

    public Optional<ResearchAgentState> findState(Long runId) {
        List<ResearchAgentState> rows = jdbcTemplate.query(
                "SELECT * FROM research_agent_state WHERE research_run_id=?", stateMapper, runId);
        return rows.isEmpty() ? Optional.<ResearchAgentState>empty() : Optional.of(rows.get(0));
    }

    public List<ResearchAgentDecision> findDecisions(Long runId) {
        return jdbcTemplate.query("SELECT * FROM research_agent_decision WHERE research_run_id=? "
                + "ORDER BY iteration ASC,id ASC", decisionMapper, runId);
    }

    public List<ResearchToolObservation> findObservations(Long runId) {
        return jdbcTemplate.query("SELECT * FROM research_tool_observation WHERE research_run_id=? "
                + "ORDER BY id ASC", observationMapper, runId);
    }

    public ResearchAgentTraceView findTrace(Long runId) {
        ResearchAgentTraceView value = new ResearchAgentTraceView();
        value.setState(findState(runId).orElse(null));
        value.setDecisions(findDecisions(runId));
        value.setObservations(findObservations(runId));
        return value;
    }

    private Long requiredKey(KeyHolder keys) {
        Number key = keys.getKey();
        if (key == null) {
            throw new BusinessException(BizErrorCode.SQLITE_GENERATED_KEY_MISSING);
        }
        return key.longValue();
    }

    private static String joinList(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(LIST_SEPARATOR, values);
    }

    private static List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (String item : value.split(LIST_SEPARATOR, -1)) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
