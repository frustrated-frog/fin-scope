package com.finscope.dao.research.mission;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMissionTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchMissionRepository {
    private static final String LIST_SEPARATOR = "\u001F";

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ResearchMission> missionMapper = (rs, rowNum) -> {
        ResearchMission value = new ResearchMission();
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setGoal(rs.getString("goal"));
        value.setSubject(rs.getString("subject"));
        value.setScopeSummary(rs.getString("scope_summary"));
        value.setSuccessCriteria(parseList(rs.getString("success_criteria")));
        value.setStatus(rs.getString("status"));
        value.setPlanningMode(rs.getString("planning_mode"));
        value.setPlanVersion(rs.getInt("plan_version"));
        value.setMaxActions(rs.getInt("max_actions"));
        value.setActiveTaskKey(rs.getString("active_task_key"));
        value.setFallbackReason(rs.getString("fallback_reason"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<ResearchMissionTask> taskMapper = (rs, rowNum) -> {
        ResearchMissionTask value = new ResearchMissionTask();
        value.setId(rs.getLong("id"));
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setTaskKey(rs.getString("task_key"));
        value.setTitle(rs.getString("title"));
        value.setQuestion(rs.getString("question"));
        value.setTaskType(rs.getString("task_type"));
        value.setToolCode(rs.getString("tool_code"));
        value.setIntent(rs.getString("intent"));
        value.setStatus(rs.getString("status"));
        value.setDependencies(parseList(rs.getString("dependencies")));
        value.setParallelGroup(rs.getString("parallel_group"));
        value.setQueryText(rs.getString("query_text"));
        value.setRationale(rs.getString("rationale"));
        value.setExpectedEvidence(rs.getString("expected_evidence"));
        value.setOutputSummary(rs.getString("output_summary"));
        value.setEvidenceDelta(rs.getInt("evidence_delta"));
        value.setSourceDelta(rs.getInt("source_delta"));
        value.setSkipReason(rs.getString("skip_reason"));
        value.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        value.setEndedAt(TimeUtil.localDateTime(rs, "ended_at"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<ResearchMissionGap> gapMapper = (rs, rowNum) -> {
        ResearchMissionGap value = new ResearchMissionGap();
        value.setId(rs.getLong("id"));
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setAssessmentIndex(rs.getInt("assessment_index"));
        value.setAfterTaskKey(rs.getString("after_task_key"));
        value.setSufficient(rs.getInt("sufficient") == 1);
        value.setEvidenceCount(rs.getInt("evidence_count"));
        value.setSourceCount(rs.getInt("source_count"));
        value.setSupportCount(rs.getInt("support_count"));
        value.setCounterCount(rs.getInt("counter_count"));
        value.setWarnings(parseList(rs.getString("warnings")));
        value.setRecommendedIntent(rs.getString("recommended_intent"));
        value.setStateHash(rs.getString("state_hash"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public ResearchMission initialize(Long runId,
                                      String goal,
                                      String subject,
                                      String scopeSummary,
                                      List<String> successCriteria,
                                      int maxActions) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO research_mission(research_run_id,goal,subject,scope_summary,"
                        + "success_criteria,status,planning_mode,plan_version,max_actions,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,'PENDING','PENDING',0,?,?,?) "
                        + "ON CONFLICT(research_run_id) DO NOTHING",
                runId, goal, subject, scopeSummary, joinList(successCriteria), maxActions,
                TimeUtil.text(now), TimeUtil.text(now));
        return findMission(runId)
                .orElseThrow(() -> new IllegalStateException("Research mission initialization failed: " + runId));
    }

    @Transactional
    public ResearchMission replacePlan(Long runId,
                                       String planningMode,
                                       String scopeSummary,
                                       List<String> successCriteria,
                                       List<ResearchMissionTask> tasks,
                                       String fallbackReason) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("DELETE FROM research_mission_task WHERE research_run_id=?", runId);
        if (tasks != null) {
            for (ResearchMissionTask task : tasks) {
                insertTask(runId, task, now);
            }
        }
        int updated = jdbcTemplate.update("UPDATE research_mission SET scope_summary=?,success_criteria=?,"
                        + "status='RUNNING',planning_mode=?,plan_version=plan_version+1,active_task_key=NULL,"
                        + "fallback_reason=?,updated_at=? WHERE research_run_id=?",
                scopeSummary, joinList(successCriteria), planningMode, fallbackReason, TimeUtil.text(now), runId);
        if (updated != 1) {
            throw new IllegalStateException("Research mission not found: " + runId);
        }
        return findMission(runId).get();
    }

    @Transactional
    public boolean startTask(Long runId, String taskKey) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE research_mission_task SET status='RUNNING',started_at=?,"
                        + "ended_at=NULL,skip_reason=NULL,updated_at=? "
                        + "WHERE research_run_id=? AND task_key=? AND status IN ('PENDING','INTERRUPTED','FAILED')",
                TimeUtil.text(now), TimeUtil.text(now), runId, taskKey);
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update("UPDATE research_mission SET status='RUNNING',active_task_key=?,updated_at=? "
                        + "WHERE research_run_id=?",
                taskKey, TimeUtil.text(now), runId);
        return true;
    }

    @Transactional
    public boolean completeTask(Long runId,
                                String taskKey,
                                String outputSummary,
                                int evidenceDelta,
                                int sourceDelta) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE research_mission_task SET status='COMPLETED',output_summary=?,"
                        + "evidence_delta=?,source_delta=?,skip_reason=NULL,ended_at=?,updated_at=? "
                        + "WHERE research_run_id=? AND task_key=? AND status='RUNNING'",
                outputSummary, evidenceDelta, sourceDelta, TimeUtil.text(now), TimeUtil.text(now), runId, taskKey);
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update("UPDATE research_mission SET active_task_key=NULL,updated_at=? "
                        + "WHERE research_run_id=? AND active_task_key=?",
                TimeUtil.text(now), runId, taskKey);
        return true;
    }

    @Transactional
    public boolean failTask(Long runId, String taskKey, String outputSummary) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE research_mission_task SET status='FAILED',output_summary=?,"
                        + "ended_at=?,updated_at=? WHERE research_run_id=? AND task_key=? AND status='RUNNING'",
                outputSummary, TimeUtil.text(now), TimeUtil.text(now), runId, taskKey);
        if (updated == 1) {
            jdbcTemplate.update("UPDATE research_mission SET active_task_key=NULL,updated_at=? "
                            + "WHERE research_run_id=? AND active_task_key=?",
                    TimeUtil.text(now), runId, taskKey);
        }
        return updated == 1;
    }

    public int skipPendingTasksByTool(Long runId, String toolCode, String reason) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update("UPDATE research_mission_task SET status='SKIPPED',skip_reason=?,"
                        + "ended_at=?,updated_at=? WHERE research_run_id=? AND tool_code=? AND status='PENDING'",
                reason, TimeUtil.text(now), TimeUtil.text(now), runId, toolCode);
    }

    public int interruptRunning(String reason) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE research_mission_task SET status='INTERRUPTED',"
                        + "output_summary=?,ended_at=?,updated_at=? WHERE status='RUNNING'",
                reason, TimeUtil.text(now), TimeUtil.text(now));
        jdbcTemplate.update("UPDATE research_mission SET status='INTERRUPTED',active_task_key=NULL,updated_at=? "
                        + "WHERE status IN ('PLANNING','RUNNING')",
                TimeUtil.text(now));
        return updated;
    }

    public boolean updateMissionStatus(Long runId, String status) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update("UPDATE research_mission SET status=?,active_task_key=NULL,updated_at=? "
                        + "WHERE research_run_id=?",
                status, TimeUtil.text(now), runId) == 1;
    }

    public synchronized ResearchMissionGap appendGap(ResearchMissionGap gap) {
        Integer next = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(assessment_index),0)+1 "
                        + "FROM research_mission_gap WHERE research_run_id=?",
                Integer.class, gap.getResearchRunId());
        gap.setAssessmentIndex(next == null ? 1 : next);
        gap.setCreatedAt(LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO research_mission_gap(research_run_id,assessment_index,after_task_key,"
                        + "sufficient,evidence_count,source_count,support_count,counter_count,warnings,"
                        + "recommended_intent,state_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                gap.getResearchRunId(), gap.getAssessmentIndex(), gap.getAfterTaskKey(),
                gap.isSufficient() ? 1 : 0, gap.getEvidenceCount(), gap.getSourceCount(),
                gap.getSupportCount(), gap.getCounterCount(), joinList(gap.getWarnings()),
                gap.getRecommendedIntent(), gap.getStateHash(), TimeUtil.text(gap.getCreatedAt()));
        return gap;
    }

    public Optional<ResearchMission> findMission(Long runId) {
        List<ResearchMission> rows = jdbcTemplate.query(
                "SELECT * FROM research_mission WHERE research_run_id=?", missionMapper, runId);
        return rows.isEmpty() ? Optional.<ResearchMission>empty() : Optional.of(rows.get(0));
    }

    public Optional<ResearchMissionTask> findTask(Long runId, String taskKey) {
        List<ResearchMissionTask> rows = jdbcTemplate.query(
                "SELECT * FROM research_mission_task WHERE research_run_id=? AND task_key=?",
                taskMapper, runId, taskKey);
        return rows.isEmpty() ? Optional.<ResearchMissionTask>empty() : Optional.of(rows.get(0));
    }

    public List<ResearchMissionTask> findTasks(Long runId) {
        return jdbcTemplate.query(
                "SELECT * FROM research_mission_task WHERE research_run_id=? ORDER BY id ASC",
                taskMapper, runId);
    }

    public List<ResearchMissionGap> findGaps(Long runId) {
        return jdbcTemplate.query(
                "SELECT * FROM research_mission_gap WHERE research_run_id=? ORDER BY assessment_index ASC",
                gapMapper, runId);
    }

    private void insertTask(Long runId, ResearchMissionTask task, LocalDateTime now) {
        jdbcTemplate.update("INSERT INTO research_mission_task(research_run_id,task_key,title,question,task_type,"
                        + "tool_code,intent,status,dependencies,parallel_group,query_text,rationale,expected_evidence,"
                        + "created_at,updated_at) VALUES(?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?,?)",
                runId, task.getTaskKey(), task.getTitle(), task.getQuestion(), task.getTaskType(),
                task.getToolCode(), task.getIntent(), joinList(task.getDependencies()), task.getParallelGroup(),
                task.getQueryText(), task.getRationale(), task.getExpectedEvidence(),
                TimeUtil.text(now), TimeUtil.text(now));
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
