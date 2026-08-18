package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class QuantStrategyRepository {
    @Resource private JdbcTemplate jdbcTemplate;

    private final RowMapper<QuantStrategyDraft> draftMapper = (rs, rowNum) -> {
        QuantStrategyDraft value = new QuantStrategyDraft();
        value.setId(rs.getLong("id")); value.setDatasetId(rs.getLong("dataset_id"));
        value.setPrompt(rs.getString("prompt")); value.setRawResponse(rs.getString("raw_response"));
        value.setNormalizedSpec(rs.getString("normalized_spec")); value.setStatus(rs.getString("status"));
        value.setModel(rs.getString("model")); value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setValidatedDatasetFingerprint(rs.getString("validated_dataset_fingerprint"));
        String issues = rs.getString("validation_issues");
        if (issues != null && !issues.trim().isEmpty()) value.setValidationIssues(java.util.Arrays.asList(issues.split("\\n")));
        return value;
    };
    private final RowMapper<QuantStrategyVersion> versionMapper = (rs, rowNum) -> {
        QuantStrategyVersion value = new QuantStrategyVersion();
        value.setId(rs.getLong("id")); value.setName(rs.getString("name")); value.setDatasetId(rs.getLong("dataset_id"));
        value.setVersion(rs.getInt("version")); value.setSpecJson(rs.getString("spec_json"));
        value.setStrategyFingerprint(rs.getString("strategy_fingerprint"));
        value.setDatasetFingerprint(rs.getString("dataset_fingerprint")); value.setEngineVersion(rs.getString("engine_version"));
        value.setSource(rs.getString("source")); value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); return value;
    };

    public QuantStrategyDraft saveDraft(QuantStrategyDraft value) {
        LocalDateTime now = LocalDateTime.now(); KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO quant_strategy_draft(dataset_id,prompt,raw_response,"
                    + "normalized_spec,status,model,validation_issues,validated_dataset_fingerprint,created_at) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, value.getDatasetId()); ps.setString(2, value.getPrompt()); ps.setString(3, value.getRawResponse());
            ps.setString(4, value.getNormalizedSpec()); ps.setString(5, value.getStatus()); ps.setString(6, value.getModel());
            ps.setString(7, String.join("\n", value.getValidationIssues())); ps.setString(8, value.getValidatedDatasetFingerprint());
            ps.setString(9, TimeUtil.text(now)); return ps;
        }, keys);
        value.setId(keys.getKey().longValue()); value.setCreatedAt(now); return value;
    }

    public Optional<QuantStrategyDraft> findDraft(Long id) {
        List<QuantStrategyDraft> values = jdbcTemplate.query("SELECT * FROM quant_strategy_draft WHERE id=?", draftMapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public void markDraftBuildFailed(Long id, String issue) {
        jdbcTemplate.update("UPDATE quant_strategy_draft SET status='BUILD_FAILED',validation_issues=? WHERE id=?",
                issue, id);
    }

    public QuantStrategyVersion saveVersion(QuantStrategyVersion value) {
        LocalDateTime now = LocalDateTime.now(); KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO quant_strategy_version(name,dataset_id,version,spec_json,"
                    + "strategy_fingerprint,dataset_fingerprint,engine_version,source,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, value.getName()); ps.setLong(2, value.getDatasetId()); ps.setInt(3, value.getVersion());
            ps.setString(4, value.getSpecJson()); ps.setString(5, value.getStrategyFingerprint());
            ps.setString(6, value.getDatasetFingerprint()); ps.setString(7, value.getEngineVersion());
            ps.setString(8, value.getSource()); ps.setString(9, TimeUtil.text(now)); return ps;
        }, keys);
        value.setId(keys.getKey().longValue()); return findVersion(value.getId()).orElse(value);
    }

    public Optional<QuantStrategyVersion> findVersion(Long id) {
        List<QuantStrategyVersion> values = jdbcTemplate.query("SELECT * FROM quant_strategy_version WHERE id=?", versionMapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
    public List<QuantStrategyVersion> findVersions() {
        return jdbcTemplate.query("SELECT * FROM quant_strategy_version ORDER BY id DESC", versionMapper);
    }
    public int nextVersion(String name) {
        Integer value = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM quant_strategy_version WHERE name=?",
                Integer.class, name); return value == null ? 1 : value;
    }
}
