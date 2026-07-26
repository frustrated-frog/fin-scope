package com.finscope.dao.research.evaluation;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.evaluation.ResearchEvaluationMetric;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchEvaluationRepository {
    private static final String ISSUE_SEPARATOR = "\n";

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ResearchEvaluation> evaluationMapper = (rs, rowNum) -> {
        ResearchEvaluation evaluation = new ResearchEvaluation();
        evaluation.setId(rs.getLong("id"));
        evaluation.setResearchRunId(rs.getLong("research_run_id"));
        evaluation.setEvaluatorVersion(rs.getString("evaluator_version"));
        evaluation.setInputFingerprint(rs.getString("input_fingerprint"));
        evaluation.setScore(rs.getInt("score"));
        evaluation.setGateStatus(rs.getString("gate_status"));
        evaluation.setSummary(rs.getString("summary"));
        evaluation.setCriticalIssues(parseIssues(rs.getString("critical_issues")));
        evaluation.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return evaluation;
    };

    private final RowMapper<ResearchEvaluationMetric> metricMapper = (rs, rowNum) -> {
        ResearchEvaluationMetric metric = new ResearchEvaluationMetric();
        metric.setEvaluationId(rs.getLong("evaluation_id"));
        metric.setMetricCode(rs.getString("metric_code"));
        metric.setLabel(rs.getString("label"));
        metric.setScore(rs.getInt("score"));
        metric.setMaxScore(rs.getInt("max_score"));
        metric.setStatus(rs.getString("status"));
        metric.setEvidence(rs.getString("evidence"));
        metric.setRecommendation(rs.getString("recommendation"));
        return metric;
    };

    @Transactional
    public ResearchEvaluation save(ResearchEvaluation evaluation) {
        Optional<ResearchEvaluation> existing = findByIdentity(evaluation.getResearchRunId(),
                evaluation.getEvaluatorVersion(), evaluation.getInputFingerprint());
        if (existing.isPresent()) {
            return loadMetrics(existing.get());
        }
        LocalDateTime createdAt = evaluation.getCreatedAt() == null ? LocalDateTime.now() : evaluation.getCreatedAt();
        evaluation.setCreatedAt(createdAt);
        KeyHolder keys = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO research_evaluation(research_run_id,evaluator_version,input_fingerprint,score,"
                            + "gate_status,summary,critical_issues,created_at) VALUES(?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(research_run_id,evaluator_version,input_fingerprint) DO NOTHING",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, evaluation.getResearchRunId());
            statement.setString(2, evaluation.getEvaluatorVersion());
            statement.setString(3, evaluation.getInputFingerprint());
            statement.setInt(4, evaluation.getScore());
            statement.setString(5, evaluation.getGateStatus());
            statement.setString(6, evaluation.getSummary());
            statement.setString(7, String.join(ISSUE_SEPARATOR, evaluation.getCriticalIssues()));
            statement.setString(8, TimeUtil.text(createdAt));
            return statement;
        }, keys);
        if (inserted == 0) {
            return findByIdentity(evaluation.getResearchRunId(), evaluation.getEvaluatorVersion(),
                    evaluation.getInputFingerprint()).map(this::loadMetrics)
                    .orElseThrow(() -> new IllegalStateException("Concurrent evaluation insert was not visible"));
        }
        evaluation.setId(keys.getKey().longValue());
        for (ResearchEvaluationMetric metric : evaluation.getMetrics()) {
            metric.setEvaluationId(evaluation.getId());
            jdbcTemplate.update("INSERT INTO research_evaluation_metric(evaluation_id,metric_code,label,score,"
                            + "max_score,status,evidence,recommendation) VALUES(?,?,?,?,?,?,?,?)",
                    evaluation.getId(), metric.getMetricCode(), metric.getLabel(), metric.getScore(),
                    metric.getMaxScore(), metric.getStatus(), metric.getEvidence(), metric.getRecommendation());
        }
        return loadMetrics(evaluation);
    }

    public Optional<ResearchEvaluation> findByIdentity(Long runId, String version, String fingerprint) {
        List<ResearchEvaluation> rows = jdbcTemplate.query("SELECT * FROM research_evaluation "
                        + "WHERE research_run_id=? AND evaluator_version=? AND input_fingerprint=?",
                evaluationMapper, runId, version, fingerprint);
        return rows.isEmpty() ? Optional.<ResearchEvaluation>empty() : Optional.of(rows.get(0));
    }

    public Optional<ResearchEvaluation> findLatestByRunId(Long runId) {
        List<ResearchEvaluation> rows = jdbcTemplate.query("SELECT * FROM research_evaluation "
                        + "WHERE research_run_id=? ORDER BY created_at DESC,id DESC LIMIT 1", evaluationMapper, runId);
        return rows.isEmpty() ? Optional.<ResearchEvaluation>empty() : Optional.of(loadMetrics(rows.get(0)));
    }

    public List<ResearchEvaluation> findAllByRunId(Long runId) {
        List<ResearchEvaluation> rows = jdbcTemplate.query("SELECT * FROM research_evaluation "
                + "WHERE research_run_id=? ORDER BY created_at DESC,id DESC", evaluationMapper, runId);
        for (ResearchEvaluation row : rows) {
            loadMetrics(row);
        }
        return rows;
    }

    private ResearchEvaluation loadMetrics(ResearchEvaluation evaluation) {
        evaluation.setMetrics(jdbcTemplate.query("SELECT * FROM research_evaluation_metric "
                        + "WHERE evaluation_id=? ORDER BY metric_code ASC", metricMapper, evaluation.getId()));
        return evaluation;
    }

    private List<String> parseIssues(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(Arrays.asList(value.split(ISSUE_SEPARATOR)));
    }
}
