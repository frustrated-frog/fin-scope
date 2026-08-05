package com.finscope.dao.financials;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.financials.FinancialInterpretation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class FinancialInterpretationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FinancialInterpretationRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public FinancialInterpretation save(FinancialInterpretation value) {
        try {
            if (value.getCreatedAt() == null) {
                value.setCreatedAt(LocalDateTime.now());
            }
            String resultJson = value.getResult() == null ? null : json.writeValueAsString(value.getResult());
            String errorsJson = json.writeValueAsString(value.getValidationErrors());
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO financial_interpretation(report_id,snapshot_id,generation_key," +
                                "prompt_version,model_name,status,generation_mode,result_json," +
                                "validation_errors_json,failure_code,failure_message,duration_ms,created_at," +
                                "started_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                Object[] args = {value.getReportId(), value.getSnapshotId(), value.getGenerationKey(),
                        value.getPromptVersion(), value.getModelName(), value.getStatus(),
                        value.getGenerationMode(), resultJson, errorsJson, value.getFailureCode(),
                        value.getFailureMessage(), value.getDurationMs(), text(value.getCreatedAt()),
                        text(value.getStartedAt()), text(value.getCompletedAt())};
                for (int index = 0; index < args.length; index++) {
                    statement.setObject(index + 1, args[index]);
                }
                return statement;
            }, keys);
            value.setId(keys.getKey().longValue());
            return value;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "cannot persist financial interpretation", error);
        }
    }

    public void update(FinancialInterpretation value) {
        try {
            jdbc.update("UPDATE financial_interpretation SET status=?,generation_mode=?,result_json=?," +
                            "validation_errors_json=?,failure_code=?,failure_message=?,duration_ms=?," +
                            "started_at=?,completed_at=?,model_name=? WHERE id=?",
                    value.getStatus(), value.getGenerationMode(),
                    value.getResult() == null ? null : json.writeValueAsString(value.getResult()),
                    json.writeValueAsString(value.getValidationErrors()), value.getFailureCode(),
                    value.getFailureMessage(), value.getDurationMs(), text(value.getStartedAt()),
                    text(value.getCompletedAt()), value.getModelName(), value.getId());
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "cannot update financial interpretation id=" + value.getId(), error);
        }
    }

    public Optional<FinancialInterpretation> findById(Long id) {
        List<FinancialInterpretation> rows = jdbc.query(
                "SELECT * FROM financial_interpretation WHERE id=?", this::map, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<FinancialInterpretation> findLatestDisplayable(Long reportId) {
        return first("SELECT * FROM financial_interpretation WHERE report_id=? " +
                "AND status IN ('SUCCESS','FALLBACK') ORDER BY id DESC LIMIT 1", reportId);
    }

    public Optional<FinancialInterpretation> findReusable(String generationKey) {
        return first("SELECT * FROM financial_interpretation WHERE generation_key=? " +
                "AND status IN ('QUEUED','RUNNING','VALIDATING','SUCCESS') ORDER BY id DESC LIMIT 1",
                generationKey);
    }

    public Optional<FinancialInterpretation> findRunningByReport(Long reportId) {
        return first("SELECT * FROM financial_interpretation WHERE report_id=? " +
                "AND status IN ('QUEUED','RUNNING','VALIDATING') ORDER BY id DESC LIMIT 1", reportId);
    }

    public List<FinancialInterpretation> findHistory(Long reportId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("SELECT * FROM financial_interpretation WHERE report_id=? " +
                "ORDER BY id DESC LIMIT ?", this::map, reportId, safeLimit);
    }

    public int failInterrupted() {
        return jdbc.update("UPDATE financial_interpretation SET status='FAILED'," +
                        "failure_code='INTERRUPTED',failure_message=?,completed_at=? " +
                        "WHERE status IN ('QUEUED','RUNNING','VALIDATING')",
                "服务重启中断了本次解读，请重新生成", LocalDateTime.now().toString());
    }

    private Optional<FinancialInterpretation> first(String sql, Object argument) {
        List<FinancialInterpretation> rows = jdbc.query(sql, this::map, argument);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private FinancialInterpretation map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        try {
            FinancialInterpretation value = new FinancialInterpretation();
            value.setId(rs.getLong("id"));
            value.setReportId(rs.getLong("report_id"));
            value.setSnapshotId(rs.getLong("snapshot_id"));
            value.setGenerationKey(rs.getString("generation_key"));
            value.setPromptVersion(rs.getString("prompt_version"));
            value.setModelName(rs.getString("model_name"));
            value.setStatus(rs.getString("status"));
            value.setGenerationMode(rs.getString("generation_mode"));
            String resultJson = rs.getString("result_json");
            value.setResult(resultJson == null ? null
                    : json.readValue(resultJson, FinancialInterpretation.Result.class));
            String errorsJson = rs.getString("validation_errors_json");
            value.setValidationErrors(errorsJson == null ? new ArrayList<String>()
                    : json.readValue(errorsJson, new TypeReference<List<String>>() { }));
            value.setFailureCode(rs.getString("failure_code"));
            value.setFailureMessage(rs.getString("failure_message"));
            Object duration = rs.getObject("duration_ms");
            value.setDurationMs(duration == null ? null : rs.getLong("duration_ms"));
            value.setCreatedAt(time(rs.getString("created_at")));
            value.setStartedAt(time(rs.getString("started_at")));
            value.setCompletedAt(time(rs.getString("completed_at")));
            return value;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "cannot read financial interpretation", error);
        }
    }

    private String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime time(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }
}
