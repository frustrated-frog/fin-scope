package com.finscope.dao.financials;

import com.finscope.domain.financials.FinancialAnalysisSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FinancialAnalysisSnapshotRepository {
    private final JdbcTemplate jdbc;

    public FinancialAnalysisSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized FinancialAnalysisSnapshot saveOrReuse(FinancialAnalysisSnapshot value) {
        Optional<FinancialAnalysisSnapshot> existing = findByIdentity(
                value.getReportId(), value.getAlgorithmVersion(), value.getInputHash());
        if (existing.isPresent()) {
            value.setId(existing.get().getId());
            value.setCreatedAt(existing.get().getCreatedAt());
            return value;
        }
        if (value.getCreatedAt() == null) {
            value.setCreatedAt(LocalDateTime.now());
        }
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO financial_analysis_snapshot(report_id,algorithm_version,source_hash," +
                            "input_hash,payload_json,quality_level,created_at) VALUES(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, value.getReportId());
            statement.setString(2, value.getAlgorithmVersion());
            statement.setString(3, value.getSourceHash());
            statement.setString(4, value.getInputHash());
            statement.setString(5, value.getPayloadJson());
            statement.setString(6, value.getQualityLevel());
            statement.setString(7, value.getCreatedAt().toString());
            return statement;
        }, keys);
        value.setId(keys.getKey().longValue());
        return value;
    }

    public Optional<FinancialAnalysisSnapshot> findById(Long id) {
        List<FinancialAnalysisSnapshot> rows = jdbc.query(
                "SELECT * FROM financial_analysis_snapshot WHERE id=?", this::map, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<FinancialAnalysisSnapshot> findLatest(Long reportId) {
        List<FinancialAnalysisSnapshot> rows = jdbc.query(
                "SELECT * FROM financial_analysis_snapshot WHERE report_id=? ORDER BY id DESC LIMIT 1",
                this::map, reportId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<FinancialAnalysisSnapshot> findByIdentity(
            Long reportId, String algorithmVersion, String inputHash) {
        List<FinancialAnalysisSnapshot> rows = jdbc.query(
                "SELECT * FROM financial_analysis_snapshot WHERE report_id=? AND algorithm_version=? " +
                        "AND input_hash=? LIMIT 1", this::map, reportId, algorithmVersion, inputHash);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private FinancialAnalysisSnapshot map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        FinancialAnalysisSnapshot value = new FinancialAnalysisSnapshot();
        value.setId(rs.getLong("id"));
        value.setReportId(rs.getLong("report_id"));
        value.setAlgorithmVersion(rs.getString("algorithm_version"));
        value.setSourceHash(rs.getString("source_hash"));
        value.setInputHash(rs.getString("input_hash"));
        value.setPayloadJson(rs.getString("payload_json"));
        value.setQualityLevel(rs.getString("quality_level"));
        value.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        return value;
    }
}
