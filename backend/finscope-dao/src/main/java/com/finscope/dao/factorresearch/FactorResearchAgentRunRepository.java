package com.finscope.dao.factorresearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorResearchAgentRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class FactorResearchAgentRunRepository {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<List<String>>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FactorResearchAgentRunRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    private final RowMapper<FactorResearchAgentRun> mapper = (rs, row) -> {
        FactorResearchAgentRun value = new FactorResearchAgentRun();
        value.setId(rs.getLong("id")); value.setDatasetId(rs.getLong("dataset_id"));
        value.setDatasetFingerprint(rs.getString("dataset_fingerprint"));
        value.setFactor(new FactorIdentity(rs.getString("factor_namespace"), rs.getString("factor_code"), rs.getString("factor_version")));
        long draftId = rs.getLong("research_draft_id"); value.setResearchDraftId(rs.wasNull() ? null : draftId);
        value.setQuestion(rs.getString("question")); value.setStatus(rs.getString("status"));
        value.setPlan(read(rs.getString("plan_json"))); value.setAllowedTools(read(rs.getString("allowed_tools_json")));
        value.setMaxToolCalls(rs.getInt("max_tool_calls")); value.setToolCallsUsed(rs.getInt("tool_calls_used"));
        value.setMaxLlmCalls(rs.getInt("max_llm_calls")); value.setLlmCallsUsed(rs.getInt("llm_calls_used"));
        value.setMaxRunSeconds(rs.getInt("max_run_seconds")); value.setEvidenceJson(rs.getString("evidence_json"));
        value.setEvidenceHash(rs.getString("evidence_hash")); value.setFindingJson(rs.getString("finding_json"));
        value.setStopReason(rs.getString("stop_reason")); value.setCreatedAt(time(rs.getString("created_at")));
        value.setApprovedAt(time(rs.getString("approved_at"))); value.setCompletedAt(time(rs.getString("completed_at")));
        return value;
    };

    public FactorResearchAgentRun save(FactorResearchAgentRun value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO factor_research_agent_run(dataset_id,dataset_fingerprint,factor_namespace,factor_code,factor_version,"
                            + "research_draft_id,question,status,plan_json,allowed_tools_json,max_tool_calls,max_llm_calls,max_run_seconds,created_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, value.getDatasetId()); statement.setString(2, value.getDatasetFingerprint());
            statement.setString(3, value.getFactor().getNamespace()); statement.setString(4, value.getFactor().getCode());
            statement.setString(5, value.getFactor().getVersion());
            if (value.getResearchDraftId() == null) statement.setObject(6, null); else statement.setLong(6, value.getResearchDraftId());
            statement.setString(7, value.getQuestion()); statement.setString(8, value.getStatus());
            statement.setString(9, write(value.getPlan())); statement.setString(10, write(value.getAllowedTools()));
            statement.setInt(11, value.getMaxToolCalls()); statement.setInt(12, value.getMaxLlmCalls());
            statement.setInt(13, value.getMaxRunSeconds()); statement.setString(14, value.getCreatedAt().toString());
            return statement;
        }, key);
        value.setId(key.getKey().longValue()); return value;
    }

    public Optional<FactorResearchAgentRun> findById(Long id) {
        List<FactorResearchAgentRun> values = jdbc.query("SELECT * FROM factor_research_agent_run WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public boolean transition(Long id, String expected, String next, LocalDateTime at) {
        if ("APPROVED".equals(next)) {
            return jdbc.update("UPDATE factor_research_agent_run SET status=?,approved_at=? WHERE id=? AND status=?",
                    next, at.toString(), id, expected) == 1;
        }
        return jdbc.update("UPDATE factor_research_agent_run SET status=? WHERE id=? AND status=?",
                next, id, expected) == 1;
    }

    public void complete(Long id, String status, int toolCalls, String evidenceJson, String evidenceHash,
                         String findingJson, String stopReason, LocalDateTime completedAt) {
        jdbc.update("UPDATE factor_research_agent_run SET status=?,tool_calls_used=?,evidence_json=?,evidence_hash=?,"
                        + "finding_json=?,stop_reason=?,completed_at=? WHERE id=?",
                status, toolCalls, evidenceJson, evidenceHash, findingJson, stopReason, completedAt.toString(), id);
    }

    private String write(List<String> value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalArgumentException(ex); } }
    private List<String> read(String value) { try { return value == null ? Collections.emptyList() : json.readValue(value, STRINGS); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private LocalDateTime time(String value) { return value == null || value.isEmpty() ? null : LocalDateTime.parse(value); }
}
