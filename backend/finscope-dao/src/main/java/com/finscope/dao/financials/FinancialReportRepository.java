package com.finscope.dao.financials;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.financials.FinancialValueOrigin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FinancialReportRepository {
    private final JdbcTemplate jdbc;

    public FinancialReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<FinancialReport> reportMapper = (rs, rowNum) -> {
        FinancialReport value = new FinancialReport();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        value.setPeriodEnd(TimeUtil.localDate(rs, "period_end"));
        value.setReportType(FinancialReportType.valueOf(rs.getString("report_type")));
        value.setScope(rs.getString("scope"));
        value.setCurrency(rs.getString("currency"));
        value.setPublishedAt(TimeUtil.localDateTime(rs, "published_at"));
        Object audited = rs.getObject("audited");
        value.setAudited(audited == null ? null : rs.getInt("audited") != 0);
        value.setQualityStatus(FinancialQualityStatus.valueOf(rs.getString("quality_status")));
        value.setSourceCode(rs.getString("source_code"));
        value.setWarningMessage(rs.getString("warning_message"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<FinancialLineItem> lineMapper = (rs, rowNum) -> {
        FinancialLineItem value = new FinancialLineItem();
        value.setId(rs.getLong("id"));
        value.setReportId(rs.getLong("report_id"));
        value.setStatementType(FinancialStatementType.valueOf(rs.getString("statement_type")));
        value.setSourceLabel(rs.getString("source_label"));
        value.setConceptCode(rs.getString("concept_code"));
        value.setPeriodRole(rs.getString("period_role"));
        value.setNormalizedValue(decimal(rs.getString("normalized_value")));
        value.setCurrency(rs.getString("currency"));
        value.setUnitMultiplier(decimal(rs.getString("unit_multiplier")));
        value.setValueOrigin(FinancialValueOrigin.valueOf(rs.getString("value_origin")));
        value.setSourceField(rs.getString("source_field"));
        value.setSourceCode(rs.getString("source_code"));
        value.setDisplayOrder(rs.getInt("display_order"));
        value.setQualityStatus(FinancialQualityStatus.valueOf(rs.getString("quality_status")));
        return value;
    };

    @Transactional
    public FinancialReport saveReport(FinancialReport report) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO financial_report(" +
                        "instrument_id,period_end,report_type,scope,currency,published_at,audited," +
                        "quality_status,source_code,warning_message,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(instrument_id,period_end,report_type,scope) DO UPDATE SET " +
                        "currency=excluded.currency,published_at=excluded.published_at," +
                        "audited=excluded.audited,quality_status=excluded.quality_status," +
                        "source_code=excluded.source_code,warning_message=excluded.warning_message," +
                        "updated_at=excluded.updated_at",
                report.getInstrumentId(), TimeUtil.text(report.getPeriodEnd()), report.getReportType().name(),
                report.getScope(), report.getCurrency(), TimeUtil.text(report.getPublishedAt()),
                report.getAudited() == null ? null : report.getAudited() ? 1 : 0,
                report.getQualityStatus().name(), report.getSourceCode(), report.getWarningMessage(),
                TimeUtil.text(now), TimeUtil.text(now));
        FinancialReport stored = findByIdentity(report).orElseThrow(IllegalStateException::new);
        report.setId(stored.getId());
        report.setCreatedAt(stored.getCreatedAt());
        report.setUpdatedAt(stored.getUpdatedAt());
        return report;
    }

    @Transactional
    public void replaceLineItems(Long reportId, String sourceCode, List<FinancialLineItem> items) {
        jdbc.update("DELETE FROM financial_line_item WHERE report_id=? AND source_code=?",
                reportId, sourceCode);
        for (FinancialLineItem item : items) {
            jdbc.update("INSERT INTO financial_line_item(" +
                            "report_id,statement_type,source_label,concept_code,period_role,normalized_value," +
                            "currency,unit_multiplier,value_origin,source_field,source_code,display_order," +
                            "quality_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    reportId, item.getStatementType().name(), item.getSourceLabel(), item.getConceptCode(),
                    item.getPeriodRole(), text(item.getNormalizedValue()), item.getCurrency(),
                    text(item.getUnitMultiplier() == null ? BigDecimal.ONE : item.getUnitMultiplier()),
                    item.getValueOrigin().name(), item.getSourceField(), sourceCode,
                    item.getDisplayOrder() == null ? 0 : item.getDisplayOrder(),
                    item.getQualityStatus().name());
        }
        List<FinancialLineItem> stored = jdbc.query(
                "SELECT * FROM financial_line_item WHERE report_id=? AND source_code=? ORDER BY id",
                lineMapper, reportId, sourceCode);
        for (int index = 0; index < items.size() && index < stored.size(); index++) {
            items.get(index).setId(stored.get(index).getId());
        }
    }

    public List<FinancialReport> findReports(Long instrumentId) {
        return jdbc.query("SELECT * FROM financial_report WHERE instrument_id=? " +
                "ORDER BY period_end DESC,id DESC", reportMapper, instrumentId);
    }

    public Optional<FinancialReport> findById(Long id) {
        List<FinancialReport> values = jdbc.query(
                "SELECT * FROM financial_report WHERE id=?", reportMapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<FinancialReport> findReport(Long instrumentId, java.time.LocalDate periodEnd,
                                                FinancialReportType type, String scope) {
        List<FinancialReport> values = jdbc.query("SELECT * FROM financial_report WHERE " +
                        "instrument_id=? AND period_end=? AND report_type=? AND scope=?",
                reportMapper, instrumentId, TimeUtil.text(periodEnd), type.name(), scope);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<FinancialLineItem> findLineItems(Long reportId, FinancialStatementType type) {
        return jdbc.query("SELECT * FROM financial_line_item WHERE report_id=? AND statement_type=? " +
                        "ORDER BY display_order,id",
                lineMapper, reportId, type.name());
    }

    public List<FinancialLineItem> findAllLineItems(Long reportId) {
        return jdbc.query("SELECT * FROM financial_line_item WHERE report_id=? " +
                "ORDER BY statement_type,display_order,id", lineMapper, reportId);
    }

    public List<FinancialMetric> findMetrics(Long reportId) {
        return jdbc.query("SELECT * FROM financial_metric WHERE report_id=? ORDER BY id", (rs, rowNum) -> {
            FinancialMetric value = new FinancialMetric();
            value.setId(rs.getLong("id"));
            value.setReportId(rs.getLong("report_id"));
            value.setMetricCode(rs.getString("metric_code"));
            value.setLabel(rs.getString("label"));
            value.setValue(decimal(rs.getString("value")));
            value.setUnit(rs.getString("unit"));
            value.setFormulaVersion(rs.getString("formula_version"));
            value.setInputRefs(rs.getString("input_refs"));
            value.setQualityStatus(FinancialQualityStatus.valueOf(rs.getString("quality_status")));
            return value;
        }, reportId);
    }

    public List<FinancialFinding> findFindings(Long reportId) {
        return jdbc.query("SELECT * FROM financial_finding WHERE report_id=? ORDER BY id", (rs, rowNum) -> {
            FinancialFinding value = new FinancialFinding();
            value.setId(rs.getLong("id"));
            value.setReportId(rs.getLong("report_id"));
            value.setRuleCode(rs.getString("rule_code"));
            value.setRuleVersion(rs.getString("rule_version"));
            value.setSeverity(rs.getString("severity"));
            value.setDirection(rs.getString("direction"));
            value.setTitle(rs.getString("title"));
            value.setExplanation(rs.getString("explanation"));
            value.setMetricRefs(rs.getString("metric_refs"));
            value.setLimitations(rs.getString("limitations"));
            return value;
        }, reportId);
    }

    @Transactional
    public void replaceAnalysis(Long reportId, List<FinancialMetric> metrics,
                                List<FinancialFinding> findings) {
        jdbc.update("DELETE FROM financial_metric WHERE report_id=?", reportId);
        jdbc.update("DELETE FROM financial_finding WHERE report_id=?", reportId);
        for (FinancialMetric metric : metrics) {
            jdbc.update("INSERT INTO financial_metric(report_id,metric_code,label,value,unit," +
                            "formula_version,input_refs,quality_status) VALUES(?,?,?,?,?,?,?,?)",
                    reportId, metric.getMetricCode(), metric.getLabel(), text(metric.getValue()),
                    metric.getUnit(), metric.getFormulaVersion(), metric.getInputRefs(),
                    metric.getQualityStatus().name());
        }
        for (FinancialFinding finding : findings) {
            jdbc.update("INSERT INTO financial_finding(report_id,rule_code,rule_version,severity," +
                            "direction,title,explanation,metric_refs,limitations) VALUES(?,?,?,?,?,?,?,?,?)",
                    reportId, finding.getRuleCode(), finding.getRuleVersion(), finding.getSeverity(),
                    finding.getDirection(), finding.getTitle(), finding.getExplanation(),
                    finding.getMetricRefs(), finding.getLimitations());
        }
    }

    private Optional<FinancialReport> findByIdentity(FinancialReport report) {
        List<FinancialReport> values = jdbc.query("SELECT * FROM financial_report WHERE " +
                        "instrument_id=? AND period_end=? AND report_type=? AND scope=?",
                reportMapper, report.getInstrumentId(), TimeUtil.text(report.getPeriodEnd()),
                report.getReportType().name(), report.getScope());
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
