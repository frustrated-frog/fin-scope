package com.finscope.dao.research.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.quality.ResearchTaskContract;
import com.finscope.domain.research.quality.ResearchQualityAssessment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchQualityRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResearchTaskContract initializeContract(ResearchTaskContract contract) {
        jdbcTemplate.update("INSERT OR IGNORE INTO research_task_contract(research_run_id,contract_json) VALUES(?,?)",
                contract.getResearchRunId(), encode(contract));
        return findContract(contract.getResearchRunId()).orElseThrow(
                () -> new IllegalStateException("研究任务契约保存失败"));
    }

    public Optional<ResearchTaskContract> findContract(Long runId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT contract_json FROM research_task_contract WHERE research_run_id=?", String.class, runId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(decode(rows.get(0), ResearchTaskContract.class));
    }

    public ResearchQualityAssessment save(ResearchQualityAssessment assessment) {
        jdbcTemplate.update("INSERT OR IGNORE INTO research_quality_assessment(research_run_id,fingerprint,assessment_json)"
                        + " VALUES(?,?,?)", assessment.getContract().getResearchRunId(), assessment.getFingerprint(),
                encode(assessment));
        return find(assessment.getContract().getResearchRunId(), assessment.getFingerprint()).orElseThrow(
                () -> new IllegalStateException("研究证据审计保存失败"));
    }

    public Optional<ResearchQualityAssessment> find(Long runId, String fingerprint) {
        return read("SELECT assessment_json FROM research_quality_assessment WHERE research_run_id=? AND fingerprint=?",
                runId, fingerprint);
    }

    public Optional<ResearchQualityAssessment> findLatest(Long runId) {
        return read("SELECT assessment_json FROM research_quality_assessment WHERE research_run_id=? ORDER BY id DESC LIMIT 1", runId);
    }

    public List<ResearchQualityAssessment> findHistory(Long runId) {
        List<ResearchQualityAssessment> history = jdbcTemplate.query(
                "SELECT assessment_json FROM research_quality_assessment WHERE research_run_id=? ORDER BY id DESC LIMIT 100",
                (rs, index) -> decode(rs.getString(1), ResearchQualityAssessment.class), runId);
        java.util.Collections.reverse(history);
        return history;
    }

    public boolean reserveModelAttempt(Long runId) {
        return jdbcTemplate.update("INSERT OR IGNORE INTO research_fact_extraction_attempt(research_run_id) VALUES(?)", runId) == 1;
    }

    private Optional<ResearchQualityAssessment> read(String sql, Object... args) {
        List<String> rows = jdbcTemplate.queryForList(sql, String.class, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(decode(rows.get(0), ResearchQualityAssessment.class));
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("研究质量快照序列化失败", error);
        }
    }

    private <T> T decode(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception error) {
            throw new IllegalStateException("研究质量快照读取失败", error);
        }
    }
}
