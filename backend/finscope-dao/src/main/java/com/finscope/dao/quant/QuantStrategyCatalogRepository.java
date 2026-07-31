package com.finscope.dao.quant;

import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class QuantStrategyCatalogRepository {
    @Resource private JdbcTemplate jdbcTemplate;

    public void saveSource(QuantStrategyCatalogSource source) {
        jdbcTemplate.update("INSERT INTO quant_catalog_source(code,repository_url,branch,commit_sha,status,last_synced_at,error_message) "
                        + "VALUES(?,?,?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET repository_url=excluded.repository_url,"
                        + "branch=excluded.branch,commit_sha=excluded.commit_sha,status=excluded.status,"
                        + "last_synced_at=excluded.last_synced_at,error_message=excluded.error_message",
                source.getCode(), source.getRepositoryUrl(), source.getBranch(), source.getCommitSha(), source.getStatus(),
                source.getLastSyncedAt().toString(), source.getErrorMessage());
    }

    public void upsertCandidates(String sourceCode, String commitSha, List<QuantStrategyCandidate> values, LocalDateTime now) {
        jdbcTemplate.update("UPDATE quant_strategy_candidate SET archived=1,updated_at=? WHERE source_code=?",
                now.toString(), sourceCode);
        for (QuantStrategyCandidate value : values) {
            jdbcTemplate.update("INSERT INTO quant_strategy_candidate(source_code,external_key,source_commit_sha,title,asset_class,"
                            + "reported_sharpe,reported_volatility,rebalance_cadence,implementation_url,paper_url,compatibility_status,"
                            + "adaptation_note,mapped_factors,missing_factors,archived,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?) "
                            + "ON CONFLICT(source_code,external_key) DO UPDATE SET source_commit_sha=excluded.source_commit_sha,"
                            + "title=excluded.title,asset_class=excluded.asset_class,reported_sharpe=excluded.reported_sharpe,"
                            + "reported_volatility=excluded.reported_volatility,rebalance_cadence=excluded.rebalance_cadence,"
                            + "implementation_url=excluded.implementation_url,paper_url=excluded.paper_url,"
                            + "compatibility_status=excluded.compatibility_status,adaptation_note=excluded.adaptation_note,"
                            + "mapped_factors=excluded.mapped_factors,missing_factors=excluded.missing_factors,archived=0,updated_at=excluded.updated_at",
                    sourceCode, value.getExternalKey(), commitSha, value.getTitle(), value.getAssetClass(),
                    value.getReportedSharpe(), value.getReportedVolatility(), value.getRebalanceCadence(),
                    value.getImplementationUrl(), value.getPaperUrl(), value.getCompatibilityStatus(), value.getAdaptationNote(),
                    join(value.getMappedFactors()), join(value.getMissingFactors()), now.toString(), now.toString());
        }
    }

    public Optional<QuantStrategyCatalogSource> findSource() {
        List<QuantStrategyCatalogSource> values = jdbcTemplate.query("SELECT * FROM quant_catalog_source ORDER BY last_synced_at DESC LIMIT 1",
                (rs, row) -> source(rs));
        return values.isEmpty() ? Optional.<QuantStrategyCatalogSource>empty() : Optional.of(values.get(0));
    }

    public List<QuantStrategyCandidate> findCandidates(String compatibility, String query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM quant_strategy_candidate WHERE archived=0");
        List<Object> args = new ArrayList<Object>();
        if (text(compatibility)) { sql.append(" AND compatibility_status=?"); args.add(compatibility.trim()); }
        if (text(query)) { sql.append(" AND LOWER(title) LIKE ?"); args.add("%" + query.trim().toLowerCase() + "%"); }
        sql.append(" ORDER BY CASE compatibility_status WHEN 'ADAPTABLE' THEN 0 WHEN 'NEEDS_FACTOR' THEN 1 ELSE 2 END,id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, row) -> candidate(rs), args.toArray());
    }

    public Optional<QuantStrategyCandidate> findById(Long id) {
        return first(jdbcTemplate.query("SELECT * FROM quant_strategy_candidate WHERE id=?", (rs, row) -> candidate(rs), id));
    }

    public Optional<QuantStrategyCandidate> findByExternalKey(String key) {
        return first(jdbcTemplate.query("SELECT * FROM quant_strategy_candidate WHERE external_key=?", (rs, row) -> candidate(rs), key));
    }

    public int countAll() { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quant_strategy_candidate", Integer.class); }
    public int countActive() { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quant_strategy_candidate WHERE archived=0", Integer.class); }

    public void saveOrigin(Long candidateId, Long draftId, LocalDateTime now) {
        jdbcTemplate.update("INSERT INTO quant_strategy_candidate_origin(candidate_id,draft_id,created_at) VALUES(?,?,?)",
                candidateId, draftId, now.toString());
    }

    public void linkVersionForDraft(Long draftId, Long versionId) {
        jdbcTemplate.update("UPDATE quant_strategy_candidate_origin SET version_id=? WHERE draft_id=?", versionId, draftId);
    }

    public Optional<Long> findCandidateIdByDraft(Long draftId) {
        return first(jdbcTemplate.query("SELECT candidate_id FROM quant_strategy_candidate_origin WHERE draft_id=?",
                (rs, row) -> rs.getLong(1), draftId));
    }

    public Optional<Long> findVersionIdByDraft(Long draftId) {
        return first(jdbcTemplate.query("SELECT version_id FROM quant_strategy_candidate_origin WHERE draft_id=? AND version_id IS NOT NULL",
                (rs, row) -> rs.getLong(1), draftId));
    }

    private QuantStrategyCatalogSource source(ResultSet rs) throws SQLException {
        QuantStrategyCatalogSource value = new QuantStrategyCatalogSource();
        value.setCode(rs.getString("code")); value.setRepositoryUrl(rs.getString("repository_url"));
        value.setBranch(rs.getString("branch")); value.setCommitSha(rs.getString("commit_sha"));
        value.setStatus(rs.getString("status")); value.setLastSyncedAt(LocalDateTime.parse(rs.getString("last_synced_at")));
        value.setErrorMessage(rs.getString("error_message")); return value;
    }

    private QuantStrategyCandidate candidate(ResultSet rs) throws SQLException {
        QuantStrategyCandidate value = new QuantStrategyCandidate();
        value.setId(rs.getLong("id")); value.setSourceCode(rs.getString("source_code"));
        value.setExternalKey(rs.getString("external_key")); value.setSourceCommitSha(rs.getString("source_commit_sha"));
        value.setTitle(rs.getString("title")); value.setAssetClass(rs.getString("asset_class"));
        value.setReportedSharpe(nullableDouble(rs, "reported_sharpe"));
        value.setReportedVolatility(nullableDouble(rs, "reported_volatility"));
        value.setRebalanceCadence(rs.getString("rebalance_cadence")); value.setImplementationUrl(rs.getString("implementation_url"));
        value.setPaperUrl(rs.getString("paper_url")); value.setCompatibilityStatus(rs.getString("compatibility_status"));
        value.setAdaptationNote(rs.getString("adaptation_note")); value.setMappedFactors(split(rs.getString("mapped_factors")));
        value.setMissingFactors(split(rs.getString("missing_factors"))); value.setArchived(rs.getInt("archived") == 1);
        value.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"))); value.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        return value;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column); return rs.wasNull() ? null : value;
    }
    private String join(List<String> values) { return values == null ? "" : String.join(",", values); }
    private List<String> split(String value) { return !text(value) ? Collections.<String>emptyList() : Arrays.asList(value.split(",")); }
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
    private <T> Optional<T> first(List<T> values) { return values.isEmpty() ? Optional.<T>empty() : Optional.of(values.get(0)); }
}
