package com.finscope.dao.research.runtime;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchRuntimeRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ResearchRuntimeCheckpoint> checkpointMapper = (rs, rowNum) -> {
        ResearchRuntimeCheckpoint value = new ResearchRuntimeCheckpoint();
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setStateVersion(rs.getInt("state_version"));
        value.setPhase(rs.getString("phase"));
        value.setCurrentNode(rs.getString("current_node"));
        value.setStatus(rs.getString("status"));
        value.setIteration(rs.getInt("iteration"));
        value.setConsumedActions(rs.getInt("consumed_actions"));
        value.setMaxActions(rs.getInt("max_actions"));
        value.setNoProgressCount(rs.getInt("no_progress_count"));
        value.setLastStateHash(rs.getString("last_state_hash"));
        value.setResumeCount(rs.getInt("resume_count"));
        value.setTerminationReason(rs.getString("termination_reason"));
        value.setLastError(rs.getString("last_error"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<ResearchRuntimeEvent> eventMapper = (rs, rowNum) -> {
        ResearchRuntimeEvent value = new ResearchRuntimeEvent();
        value.setId(rs.getLong("id"));
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setSequenceNo(rs.getInt("sequence_no"));
        value.setEventType(rs.getString("event_type"));
        value.setNodeId(rs.getString("node_id"));
        value.setStatus(rs.getString("status"));
        value.setActionFingerprint(rs.getString("action_fingerprint"));
        value.setInputSummary(rs.getString("input_summary"));
        value.setOutputSummary(rs.getString("output_summary"));
        value.setStateHash(rs.getString("state_hash"));
        value.setProgressDelta(rs.getInt("progress_delta"));
        value.setErrorType(rs.getString("error_type"));
        value.setErrorMessage(rs.getString("error_message"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public ResearchRuntimeCheckpoint initialize(Long runId, int maxActions) {
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update("INSERT INTO research_runtime_checkpoint("
                        + "research_run_id,state_version,phase,current_node,status,iteration,consumed_actions,max_actions,"
                        + "no_progress_count,resume_count,created_at,updated_at) VALUES(?,0,'PLAN','plan_sources',"
                        + "'READY',0,0,?,0,0,?,?) ON CONFLICT(research_run_id) DO NOTHING",
                runId, maxActions, TimeUtil.text(now), TimeUtil.text(now));
        if (inserted == 1) {
            ResearchRuntimeEvent event = new ResearchRuntimeEvent();
            event.setResearchRunId(runId);
            event.setEventType("RUN_CREATED");
            event.setNodeId("plan_sources");
            event.setStatus("READY");
            appendEvent(event);
        }
        return findCheckpoint(runId)
                .orElseThrow(() -> new IllegalStateException("Research runtime checkpoint initialization failed: " + runId));
    }

    public Optional<ResearchRuntimeCheckpoint> findCheckpoint(Long runId) {
        List<ResearchRuntimeCheckpoint> rows = jdbcTemplate.query(
                "SELECT * FROM research_runtime_checkpoint WHERE research_run_id=?", checkpointMapper, runId);
        return rows.isEmpty() ? Optional.<ResearchRuntimeCheckpoint>empty() : Optional.of(rows.get(0));
    }

    public boolean compareAndSetStatus(Long runId, int expectedVersion, String status, String currentNode) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET status=?,current_node=?,"
                        + "state_version=state_version+1,updated_at=? WHERE research_run_id=? AND state_version=?",
                status, currentNode, TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public boolean startNode(Long runId,
                             int expectedVersion,
                             String phase,
                             String currentNode,
                             int consumedActions,
                             int iteration,
                             boolean preserveTermination) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET phase=?,current_node=?,"
                        + "status='RUNNING',consumed_actions=?,iteration=MAX(iteration,?),"
                        + "termination_reason=CASE WHEN ? THEN termination_reason ELSE NULL END,last_error=NULL,"
                        + "state_version=state_version+1,updated_at=? WHERE research_run_id=? AND state_version=?",
                phase, currentNode, consumedActions, iteration, preserveTermination,
                TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public boolean completeNode(Long runId,
                                int expectedVersion,
                                String currentNode,
                                String stateHash,
                                int noProgressCount,
                                int progressDelta) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET current_node=?,last_state_hash=?,"
                        + "no_progress_count=?,state_version=state_version+1,updated_at=? "
                        + "WHERE research_run_id=? AND state_version=?",
                currentNode, stateHash, noProgressCount, TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public boolean terminate(Long runId, int expectedVersion, String reason) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET status='FINALIZING',"
                        + "termination_reason=?,state_version=state_version+1,updated_at=? "
                        + "WHERE research_run_id=? AND state_version=?",
                reason, TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public boolean resume(Long runId, int expectedVersion) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET status='RUNNING',"
                        + "resume_count=resume_count+1,"
                        + "termination_reason=CASE WHEN termination_reason='COMPLETED' THEN NULL ELSE termination_reason END,"
                        + "last_error=NULL,"
                        + "state_version=state_version+1,updated_at=? WHERE research_run_id=? AND state_version=? "
                        + "AND status IN ('INTERRUPTED','FAILED','PARTIAL_SUCCESS')",
                TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public boolean failNode(Long runId, int expectedVersion, String error) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET "
                        + "status=CASE WHEN termination_reason IS NULL THEN 'INTERRUPTED' ELSE 'TERMINATED' END,last_error=?,"
                        + "state_version=state_version+1,updated_at=? WHERE research_run_id=? AND state_version=? "
                        + "AND status NOT IN ('COMPLETED','TERMINATED','CANCELLED')",
                error, TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public boolean completeRuntime(Long runId, int expectedVersion) {
        int updated = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET "
                        + "status=CASE WHEN termination_reason IS NULL THEN 'COMPLETED' ELSE 'TERMINATED' END,"
                        + "phase='COMPLETE',current_node='complete',"
                        + "termination_reason=COALESCE(termination_reason,'COMPLETED'),"
                        + "state_version=state_version+1,updated_at=? "
                        + "WHERE research_run_id=? AND state_version=?",
                TimeUtil.text(LocalDateTime.now()), runId, expectedVersion);
        return updated == 1;
    }

    public int interruptRunning(String message) {
        List<Long> runIds = jdbcTemplate.query("SELECT research_run_id FROM research_runtime_checkpoint "
                + "WHERE status IN ('READY','RUNNING','FINALIZING')", (rs, rowNum) -> rs.getLong(1));
        int updated = 0;
        for (Long runId : runIds) {
            int claimed = jdbcTemplate.update("UPDATE research_runtime_checkpoint SET status='INTERRUPTED',"
                            + "last_error=?,state_version=state_version+1,updated_at=? "
                            + "WHERE research_run_id=? AND status IN ('READY','RUNNING','FINALIZING')",
                    message, TimeUtil.text(LocalDateTime.now()), runId);
            if (claimed == 0) {
                continue;
            }
            updated += claimed;
            ResearchRuntimeEvent event = new ResearchRuntimeEvent();
            event.setResearchRunId(runId);
            event.setEventType("RUNTIME_INTERRUPTED");
            event.setStatus("INTERRUPTED");
            event.setErrorType("PROCESS_RESTART");
            event.setErrorMessage(message);
            appendEvent(event);
        }
        return updated;
    }

    public synchronized ResearchRuntimeEvent appendEvent(ResearchRuntimeEvent event) {
        Integer next = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 "
                + "FROM research_runtime_event WHERE research_run_id=?", Integer.class, event.getResearchRunId());
        LocalDateTime now = event.getCreatedAt() == null ? LocalDateTime.now() : event.getCreatedAt();
        event.setCreatedAt(now);
        event.setSequenceNo(next == null ? 1 : next);
        jdbcTemplate.update("INSERT INTO research_runtime_event(research_run_id,sequence_no,event_type,node_id,status,"
                        + "action_fingerprint,input_summary,output_summary,state_hash,progress_delta,error_type,error_message,"
                        + "created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                event.getResearchRunId(), event.getSequenceNo(), event.getEventType(), event.getNodeId(),
                event.getStatus(), event.getActionFingerprint(), event.getInputSummary(), event.getOutputSummary(),
                event.getStateHash(), event.getProgressDelta(), event.getErrorType(), event.getErrorMessage(),
                TimeUtil.text(event.getCreatedAt()));
        return event;
    }

    public List<ResearchRuntimeEvent> findEvents(Long runId) {
        return jdbcTemplate.query("SELECT * FROM research_runtime_event WHERE research_run_id=? "
                + "ORDER BY sequence_no ASC", eventMapper, runId);
    }

    public boolean hasCompletedNode(Long runId, String nodeId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM research_runtime_event "
                        + "WHERE research_run_id=? AND node_id=? AND event_type='NODE_COMPLETED'",
                Integer.class, runId, nodeId);
        return count != null && count > 0;
    }

    public int countStartedActions(Long runId, String fingerprint) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM research_runtime_event "
                        + "WHERE research_run_id=? AND action_fingerprint=? AND event_type='NODE_STARTED'",
                Integer.class, runId, fingerprint);
        return count == null ? 0 : count;
    }
}
