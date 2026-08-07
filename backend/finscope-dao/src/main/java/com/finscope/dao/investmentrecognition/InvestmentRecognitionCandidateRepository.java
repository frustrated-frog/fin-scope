package com.finscope.dao.investmentrecognition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.finscope.common.exception.BizErrorCode;

@Repository
public class InvestmentRecognitionCandidateRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<InvestmentRecognitionCandidate> mapper = (rs, rowNum) -> {
        InvestmentRecognitionCandidate value = new InvestmentRecognitionCandidate();
        value.setId(rs.getLong("id"));
        value.setFingerprint(rs.getString("fingerprint"));
        value.setSubjectType(rs.getString("subject_type"));
        value.setSubjectCode(rs.getString("subject_code"));
        value.setSubjectName(rs.getString("subject_name"));
        value.setStatus(rs.getString("status"));
        value.setThesis(rs.getString("thesis"));
        value.setObservedChange(rs.getString("observed_change"));
        value.setMechanism(rs.getString("mechanism"));
        value.setSupportingData(readList(rs.getString("supporting_data_json")));
        value.setCounterData(readList(rs.getString("counter_data_json")));
        value.setValidationMetrics(readList(rs.getString("validation_metrics_json")));
        value.setInvalidationConditions(rs.getString("invalidation_conditions"));
        value.setHorizon(rs.getString("horizon"));
        value.setConfidence(rs.getString("confidence"));
        value.setEvidenceCompleteness(rs.getString("evidence_completeness"));
        value.setTriggerSummary(rs.getString("trigger_summary"));
        value.setDataAsOf(rs.getString("data_as_of"));
        long topicId = rs.getLong("topic_id");
        value.setTopicId(rs.wasNull() ? null : topicId);
        value.setRevision(rs.getLong("revision"));
        value.setGeneratedAt(TimeUtil.localDateTime(rs, "generated_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    public InvestmentRecognitionCandidateRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public InvestmentRecognitionCandidate saveOrRefresh(InvestmentRecognitionCandidate value) {
        Optional<InvestmentRecognitionCandidate> existing = findByFingerprint(value.getFingerprint());
        if (existing.isPresent()) {
            InvestmentRecognitionCandidate current = existing.get();
            if ("ACCEPTED".equals(current.getStatus()) || "DISMISSED".equals(current.getStatus())
                    || "INVALIDATED".equals(current.getStatus())) {
                return current;
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = jdbc.update("UPDATE investment_recognition_candidate SET subject_name=?,status=?,thesis=?,"
                            + "observed_change=?,mechanism=?,supporting_data_json=?,counter_data_json=?,"
                            + "validation_metrics_json=?,invalidation_conditions=?,horizon=?,confidence=?,"
                            + "evidence_completeness=?,trigger_summary=?,data_as_of=?,revision=revision+1,updated_at=? "
                            + "WHERE id=? AND revision=? AND status IN ('CANDIDATE','NEEDS_EVIDENCE')",
                    value.getSubjectName(), value.getStatus(), value.getThesis(), value.getObservedChange(),
                    value.getMechanism(), writeList(value.getSupportingData()), writeList(value.getCounterData()),
                    writeList(value.getValidationMetrics()), value.getInvalidationConditions(), value.getHorizon(),
                    value.getConfidence(), value.getEvidenceCompleteness(), value.getTriggerSummary(), value.getDataAsOf(),
                    TimeUtil.text(now), current.getId(), current.getRevision());
            if (updated == 0) return findById(current.getId()).orElse(current);
            return findById(current.getId()).orElse(value);
        }
        LocalDateTime now = LocalDateTime.now();
        value.setRevision(0L);
        value.setGeneratedAt(now);
        value.setUpdatedAt(now);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO investment_recognition_candidate(fingerprint,subject_type,subject_code,subject_name,"
                            + "status,thesis,observed_change,mechanism,supporting_data_json,counter_data_json,"
                            + "validation_metrics_json,invalidation_conditions,horizon,confidence,evidence_completeness,"
                            + "trigger_summary,data_as_of,topic_id,revision,generated_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            statement.setString(i++, value.getFingerprint());
            statement.setString(i++, value.getSubjectType());
            statement.setString(i++, value.getSubjectCode());
            statement.setString(i++, value.getSubjectName());
            statement.setString(i++, value.getStatus());
            statement.setString(i++, value.getThesis());
            statement.setString(i++, value.getObservedChange());
            statement.setString(i++, value.getMechanism());
            statement.setString(i++, writeList(value.getSupportingData()));
            statement.setString(i++, writeList(value.getCounterData()));
            statement.setString(i++, writeList(value.getValidationMetrics()));
            statement.setString(i++, value.getInvalidationConditions());
            statement.setString(i++, value.getHorizon());
            statement.setString(i++, value.getConfidence());
            statement.setString(i++, value.getEvidenceCompleteness());
            statement.setString(i++, value.getTriggerSummary());
            statement.setString(i++, value.getDataAsOf());
            statement.setObject(i++, value.getTopicId());
            statement.setLong(i++, value.getRevision());
            statement.setString(i++, TimeUtil.text(now));
            statement.setString(i, TimeUtil.text(now));
            return statement;
        }, keys);
        value.setId(keys.getKey().longValue());
        return findById(value.getId()).orElse(value);
    }

    public List<InvestmentRecognitionCandidate> findAll() {
        return jdbc.query("SELECT * FROM investment_recognition_candidate ORDER BY updated_at DESC,id DESC", mapper);
    }

    public List<InvestmentRecognitionCandidate> findByStatus(String status) {
        return jdbc.query("SELECT * FROM investment_recognition_candidate WHERE status=? ORDER BY updated_at DESC,id DESC",
                mapper, status);
    }

    public Optional<InvestmentRecognitionCandidate> findById(Long id) {
        List<InvestmentRecognitionCandidate> values = jdbc.query(
                "SELECT * FROM investment_recognition_candidate WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public boolean updateStatus(Long id, String status, long expectedRevision, Long topicId) {
        return jdbc.update("UPDATE investment_recognition_candidate SET status=?,topic_id=?,revision=revision+1,updated_at=? "
                        + "WHERE id=? AND revision=?", status, topicId, TimeUtil.text(LocalDateTime.now()), id,
                expectedRevision) == 1;
    }

    private Optional<InvestmentRecognitionCandidate> findByFingerprint(String fingerprint) {
        List<InvestmentRecognitionCandidate> values = jdbc.query(
                "SELECT * FROM investment_recognition_candidate WHERE fingerprint=?", mapper, fingerprint);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private String writeList(List<String> values) {
        try {
            return json.writeValueAsString(values == null ? new ArrayList<String>() : values);
        } catch (Exception error) {
            throw new BusinessException(BizErrorCode.INVESTMENT_KNOWLEDGE_SERIALIZE_FAILED, error);
        }
    }

    private List<String> readList(String value) {
        try {
            return value == null ? new ArrayList<String>() : json.readValue(value, STRING_LIST);
        } catch (Exception error) {
            throw new BusinessException(BizErrorCode.INVESTMENT_KNOWLEDGE_READ_FAILED, error);
        }
    }
}
