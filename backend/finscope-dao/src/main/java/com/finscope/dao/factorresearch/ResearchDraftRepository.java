package com.finscope.dao.factorresearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.ResearchDraft;
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
public class ResearchDraftRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ResearchDraftRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private final RowMapper<ResearchDraft> mapper = (rs, rowNum) -> {
        ResearchDraft value = new ResearchDraft();
        value.setId(rs.getLong("id"));
        value.setSourceType(rs.getString("source_type"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setInstrumentName(rs.getString("instrument_name"));
        value.setObservedAt(LocalDateTime.parse(rs.getString("observed_at")));
        value.setSignalCode(rs.getString("signal_code"));
        value.setFactor(new FactorIdentity(rs.getString("factor_namespace"),
                rs.getString("factor_code"), rs.getString("factor_version")));
        value.setSnapshotId(rs.getLong("snapshot_id"));
        value.setSnapshotFingerprint(rs.getString("snapshot_fingerprint"));
        value.setEvidenceRefs(readList(rs.getString("evidence_refs_json")));
        value.setObjectiveTags(readList(rs.getString("objective_tags_json")));
        value.setEvaluationMode(rs.getString("evaluation_mode"));
        value.setStatus(rs.getString("status"));
        value.setRequiredNextSteps(readList(rs.getString("required_next_steps_json")));
        value.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        return value;
    };

    public ResearchDraft save(ResearchDraft value) {
        value.validate();
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO factor_research_draft(source_type,instrument_code,instrument_name,"
                            + "observed_at,signal_code,factor_namespace,factor_code,factor_version,snapshot_id,"
                            + "snapshot_fingerprint,evidence_refs_json,objective_tags_json,evaluation_mode,status,"
                            + "required_next_steps_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, value.getSourceType());
            statement.setString(2, value.getInstrumentCode());
            statement.setString(3, value.getInstrumentName());
            statement.setString(4, value.getObservedAt().toString());
            statement.setString(5, value.getSignalCode());
            statement.setString(6, value.getFactor().getNamespace());
            statement.setString(7, value.getFactor().getCode());
            statement.setString(8, value.getFactor().getVersion());
            statement.setLong(9, value.getSnapshotId());
            statement.setString(10, value.getSnapshotFingerprint());
            statement.setString(11, write(value.getEvidenceRefs()));
            statement.setString(12, write(value.getObjectiveTags()));
            statement.setString(13, value.getEvaluationMode());
            statement.setString(14, value.getStatus());
            statement.setString(15, write(value.getRequiredNextSteps()));
            statement.setString(16, value.getCreatedAt().toString());
            return statement;
        }, key);
        value.setId(key.getKey().longValue());
        return value;
    }

    public Optional<ResearchDraft> findById(Long id) {
        List<ResearchDraft> values = jdbc.query("SELECT * FROM factor_research_draft WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private String write(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("research draft evidence is not serializable", ex);
        }
    }

    private List<String> readList(String value) {
        try {
            return value == null ? Collections.emptyList() : json.readValue(value, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("stored research draft evidence is invalid", ex);
        }
    }
}
