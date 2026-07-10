package com.finscope.dao.attribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AttributionRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RowMapper<AttributionReport> reportMapper = (rs, rowNum) -> {
        AttributionReport report = new AttributionReport();
        report.setId(rs.getLong("id"));
        report.setInstrumentCode(rs.getString("instrument_code"));
        report.setInstrumentName(rs.getString("instrument_name"));
        report.setInstrumentType(rs.getString("instrument_type"));
        report.setReportDate(TimeUtil.localDate(rs, "report_date"));
        double changePct = rs.getDouble("change_pct");
        report.setChangePct(rs.wasNull() ? null : changePct);
        report.setStatus(rs.getString("status"));
        report.setSummary(rs.getString("summary"));
        report.setDrivers(parseDrivers(rs.getString("drivers_json")));
        report.setDisclaimer(rs.getString("disclaimer"));
        report.setErrorMessage(rs.getString("error_message"));
        long duration = rs.getLong("duration_ms");
        report.setDurationMs(rs.wasNull() ? null : duration);
        report.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        report.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return report;
    };

    private final RowMapper<AttributionEvidence> evidenceMapper = (rs, rowNum) -> {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setId(rs.getLong("id"));
        evidence.setReportId(rs.getLong("report_id"));
        evidence.setOrigin(rs.getString("origin"));
        evidence.setTitle(rs.getString("title"));
        evidence.setUrl(rs.getString("url"));
        evidence.setSnippet(rs.getString("snippet"));
        evidence.setSourceDomain(rs.getString("source_domain"));
        evidence.setSourceTier(rs.getString("source_tier"));
        int relevance = rs.getInt("relevance");
        evidence.setRelevance(rs.wasNull() ? null : relevance);
        evidence.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return evidence;
    };

    /** 创建报告（GENERATING 初始态），返回带 id 的报告。 */
    public AttributionReport createReport(AttributionReport report) {
        LocalDateTime now = LocalDateTime.now();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO attribution_report(instrument_code,instrument_name,instrument_type,report_date,"
                            + "change_pct,status,summary,drivers_json,disclaimer,error_message,duration_ms,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, report.getInstrumentCode());
            ps.setString(2, report.getInstrumentName());
            ps.setString(3, report.getInstrumentType());
            ps.setString(4, TimeUtil.text(report.getReportDate()));
            if (report.getChangePct() == null) {
                ps.setObject(5, null);
            } else {
                ps.setDouble(5, report.getChangePct());
            }
            ps.setString(6, report.getStatus());
            ps.setString(7, report.getSummary());
            ps.setString(8, writeDrivers(report.getDrivers()));
            ps.setString(9, report.getDisclaimer());
            ps.setString(10, report.getErrorMessage());
            if (report.getDurationMs() == null) {
                ps.setObject(11, null);
            } else {
                ps.setLong(11, report.getDurationMs());
            }
            ps.setString(12, TimeUtil.text(report.getCreatedAt()));
            ps.setString(13, TimeUtil.text(report.getUpdatedAt()));
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            report.setId(keyHolder.getKey().longValue());
        }
        return report;
    }

    /** 完成/失败时更新报告结果。 */
    public void updateResult(AttributionReport report) {
        jdbcTemplate.update("UPDATE attribution_report SET status=?, summary=?, drivers_json=?, disclaimer=?, "
                        + "error_message=?, duration_ms=?, change_pct=?, updated_at=? WHERE id=?",
                report.getStatus(), report.getSummary(), writeDrivers(report.getDrivers()), report.getDisclaimer(),
                report.getErrorMessage(), report.getDurationMs(), report.getChangePct(),
                TimeUtil.text(LocalDateTime.now()), report.getId());
    }

    public Optional<AttributionReport> findById(Long id) {
        List<AttributionReport> list = jdbcTemplate.query(
                "SELECT * FROM attribution_report WHERE id = ?", reportMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 某标的最新一条报告。 */
    public Optional<AttributionReport> findLatestByCode(String instrumentCode) {
        List<AttributionReport> list = jdbcTemplate.query(
                "SELECT * FROM attribution_report WHERE instrument_code = ? ORDER BY id DESC LIMIT 1",
                reportMapper, instrumentCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<AttributionReport> findHistoryByCode(String instrumentCode, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM attribution_report WHERE instrument_code = ? ORDER BY id DESC LIMIT ?",
                reportMapper, instrumentCode, limit);
    }

    public void saveEvidence(AttributionEvidence evidence) {
        evidence.setCreatedAt(LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO attribution_evidence(report_id,origin,title,url,snippet,source_domain,"
                        + "source_tier,relevance,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                evidence.getReportId(), evidence.getOrigin(), evidence.getTitle(), evidence.getUrl(),
                evidence.getSnippet(), evidence.getSourceDomain(), evidence.getSourceTier(),
                evidence.getRelevance(), TimeUtil.text(evidence.getCreatedAt()));
    }

    public List<AttributionEvidence> findEvidenceByReportId(Long reportId) {
        return jdbcTemplate.query(
                "SELECT * FROM attribution_evidence WHERE report_id = ? ORDER BY relevance DESC, id ASC",
                evidenceMapper, reportId);
    }

    private String writeDrivers(List<AttributionDriver> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(drivers);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<AttributionDriver> parseDrivers(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            CollectionType type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, AttributionDriver.class);
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }
}