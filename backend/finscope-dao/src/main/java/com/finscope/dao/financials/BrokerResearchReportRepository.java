package com.finscope.dao.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.financials.BrokerResearchClaim;
import com.finscope.domain.financials.BrokerResearchForecast;
import com.finscope.domain.financials.BrokerResearchReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BrokerResearchReportRepository {
    private final JdbcTemplate jdbc;
    @SuppressWarnings("unused")
    private final ObjectMapper json;

    public BrokerResearchReportRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public BrokerResearchReport save(BrokerResearchReport report,
                                     List<BrokerResearchForecast> forecasts,
                                     List<BrokerResearchClaim> claims) {
        Optional<BrokerResearchReport> existing = findByHash(report.getFileHash());
        if (existing.isPresent()) {
            report.setId(existing.get().getId());
            return existing.get();
        }
        LocalDateTime now = LocalDateTime.now();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO broker_research_report(instrument_id,linked_financial_report_id,title," +
                            "institution,analyst,published_date,report_type,rating,target_price," +
                            "target_price_currency,source_type,source_url,original_file_name,relative_path," +
                            "file_size,file_hash,page_count,parse_status,analysis_status,quality_level," +
                            "extracted_text,analysis_json,error_message,created_at,updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setLong(index++, report.getInstrumentId());
            statement.setObject(index++, report.getLinkedFinancialReportId());
            statement.setString(index++, report.getTitle());
            statement.setString(index++, report.getInstitution());
            statement.setString(index++, report.getAnalyst());
            statement.setString(index++, TimeUtil.text(report.getPublishedDate()));
            statement.setString(index++, report.getReportType());
            statement.setString(index++, report.getRating());
            statement.setString(index++, text(report.getTargetPrice()));
            statement.setString(index++, report.getTargetPriceCurrency());
            statement.setString(index++, report.getSourceType());
            statement.setString(index++, report.getSourceUrl());
            statement.setString(index++, report.getOriginalFileName());
            statement.setString(index++, report.getRelativePath());
            statement.setObject(index++, report.getFileSize());
            statement.setString(index++, report.getFileHash());
            statement.setObject(index++, report.getPageCount());
            statement.setString(index++, report.getParseStatus());
            statement.setString(index++, report.getAnalysisStatus());
            statement.setString(index++, report.getQualityLevel());
            statement.setString(index++, report.getExtractedText());
            statement.setString(index++, report.getAnalysisJson());
            statement.setString(index++, report.getErrorMessage());
            statement.setString(index++, TimeUtil.text(report.getCreatedAt()));
            statement.setString(index, TimeUtil.text(report.getUpdatedAt()));
            return statement;
        }, keys);
        report.setId(keys.getKey().longValue());
        for (BrokerResearchForecast forecast : forecasts) saveForecast(report.getId(), forecast);
        for (BrokerResearchClaim claim : claims) saveClaim(report.getId(), claim);
        return report;
    }

    public Optional<BrokerResearchReport> findByHash(String hash) {
        List<BrokerResearchReport> values = jdbc.query(
                "SELECT * FROM broker_research_report WHERE file_hash=?", detailMapper, hash);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<BrokerResearchReport> findBySourceUrl(String sourceType, String sourceUrl) {
        List<BrokerResearchReport> values = jdbc.query(
                "SELECT id,instrument_id,linked_financial_report_id,title,institution,analyst," +
                        "published_date,report_type,rating,target_price,target_price_currency,source_type," +
                        "source_url,original_file_name,relative_path,file_size,file_hash,page_count,parse_status," +
                        "analysis_status,quality_level,error_message,created_at,updated_at " +
                        "FROM broker_research_report WHERE source_type=? AND source_url=?",
                summaryMapper, sourceType, sourceUrl);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public void attachSourceIdentity(Long id, String sourceType, String sourceUrl) {
        jdbc.update("UPDATE broker_research_report SET source_type=?,source_url=?,updated_at=? " +
                        "WHERE id=? AND source_url IS NULL",
                sourceType, sourceUrl, TimeUtil.text(LocalDateTime.now()), id);
    }

    public Optional<BrokerResearchReport> findById(Long id) {
        List<BrokerResearchReport> values = jdbc.query(
                "SELECT * FROM broker_research_report WHERE id=?", detailMapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<BrokerResearchReport> findByInstrument(Long instrumentId) {
        return jdbc.query("SELECT id,instrument_id,linked_financial_report_id,title,institution,analyst," +
                        "published_date,report_type,rating,target_price,target_price_currency,source_type," +
                        "source_url,original_file_name,relative_path,file_size,file_hash,page_count,parse_status," +
                        "analysis_status,quality_level,error_message,created_at,updated_at " +
                        "FROM broker_research_report WHERE instrument_id=? " +
                        "ORDER BY COALESCE(published_date,'') DESC,id DESC",
                summaryMapper, instrumentId);
    }

    public List<BrokerResearchForecast> findForecasts(Long reportId) {
        return jdbc.query("SELECT * FROM broker_research_forecast WHERE research_report_id=? " +
                "ORDER BY forecast_period,metric_code,id", forecastMapper, reportId);
    }

    public List<BrokerResearchClaim> findClaims(Long reportId) {
        return jdbc.query("SELECT * FROM broker_research_claim WHERE research_report_id=? " +
                "ORDER BY category,id", claimMapper, reportId);
    }

    @Transactional
    public void replaceAnalysis(BrokerResearchReport report,
                                List<BrokerResearchForecast> forecasts,
                                List<BrokerResearchClaim> claims) {
        report.setUpdatedAt(LocalDateTime.now());
        jdbc.update("UPDATE broker_research_report SET analysis_status=?,quality_level=?," +
                        "analysis_json=?,error_message=?,updated_at=? WHERE id=?",
                report.getAnalysisStatus(), report.getQualityLevel(), report.getAnalysisJson(),
                report.getErrorMessage(), TimeUtil.text(report.getUpdatedAt()), report.getId());
        jdbc.update("DELETE FROM broker_research_forecast WHERE research_report_id=?", report.getId());
        jdbc.update("DELETE FROM broker_research_claim WHERE research_report_id=?", report.getId());
        for (BrokerResearchForecast forecast : forecasts) saveForecast(report.getId(), forecast);
        for (BrokerResearchClaim claim : claims) saveClaim(report.getId(), claim);
    }

    private void saveForecast(Long reportId, BrokerResearchForecast value) {
        jdbc.update("INSERT INTO broker_research_forecast(research_report_id,metric_code,metric_label," +
                        "forecast_period,forecast_value,unit,source_quote,source_page) VALUES(?,?,?,?,?,?,?,?)",
                reportId, value.getMetricCode(), value.getMetricLabel(), TimeUtil.text(value.getForecastPeriod()),
                text(value.getForecastValue()), value.getUnit(), value.getSourceQuote(), value.getSourcePage());
        value.setResearchReportId(reportId);
    }

    private void saveClaim(Long reportId, BrokerResearchClaim value) {
        jdbc.update("INSERT INTO broker_research_claim(research_report_id,category,title,detail,claim_type," +
                        "source_quote,source_page,financial_metric_code,financial_concept_code) " +
                        "VALUES(?,?,?,?,?,?,?,?,?)",
                reportId, value.getCategory(), value.getTitle(), value.getDetail(), value.getClaimType(),
                value.getSourceQuote(), value.getSourcePage(), value.getFinancialMetricCode(),
                value.getFinancialConceptCode());
        value.setResearchReportId(reportId);
    }

    private final RowMapper<BrokerResearchReport> detailMapper = (rs, rowNum) -> {
        BrokerResearchReport value = mapSummary(rs);
        value.setExtractedText(rs.getString("extracted_text"));
        value.setAnalysisJson(rs.getString("analysis_json"));
        return value;
    };
    private final RowMapper<BrokerResearchReport> summaryMapper = (rs, rowNum) -> mapSummary(rs);
    private final RowMapper<BrokerResearchForecast> forecastMapper = (rs, rowNum) -> {
        BrokerResearchForecast value = new BrokerResearchForecast();
        value.setId(rs.getLong("id"));
        value.setResearchReportId(rs.getLong("research_report_id"));
        value.setMetricCode(rs.getString("metric_code"));
        value.setMetricLabel(rs.getString("metric_label"));
        value.setForecastPeriod(TimeUtil.localDate(rs, "forecast_period"));
        value.setForecastValue(decimal(rs.getString("forecast_value")));
        value.setUnit(rs.getString("unit"));
        value.setSourceQuote(rs.getString("source_quote"));
        Object page = rs.getObject("source_page");
        value.setSourcePage(page == null ? null : rs.getInt("source_page"));
        return value;
    };
    private final RowMapper<BrokerResearchClaim> claimMapper = (rs, rowNum) -> {
        BrokerResearchClaim value = new BrokerResearchClaim();
        value.setId(rs.getLong("id"));
        value.setResearchReportId(rs.getLong("research_report_id"));
        value.setCategory(rs.getString("category"));
        value.setTitle(rs.getString("title"));
        value.setDetail(rs.getString("detail"));
        value.setClaimType(rs.getString("claim_type"));
        value.setSourceQuote(rs.getString("source_quote"));
        Object page = rs.getObject("source_page");
        value.setSourcePage(page == null ? null : rs.getInt("source_page"));
        value.setFinancialMetricCode(rs.getString("financial_metric_code"));
        value.setFinancialConceptCode(rs.getString("financial_concept_code"));
        return value;
    };

    private BrokerResearchReport mapSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        BrokerResearchReport value = new BrokerResearchReport();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        Object linked = rs.getObject("linked_financial_report_id");
        value.setLinkedFinancialReportId(linked == null ? null : rs.getLong("linked_financial_report_id"));
        value.setTitle(rs.getString("title"));
        value.setInstitution(rs.getString("institution"));
        value.setAnalyst(rs.getString("analyst"));
        value.setPublishedDate(TimeUtil.localDate(rs, "published_date"));
        value.setReportType(rs.getString("report_type"));
        value.setRating(rs.getString("rating"));
        value.setTargetPrice(decimal(rs.getString("target_price")));
        value.setTargetPriceCurrency(rs.getString("target_price_currency"));
        value.setSourceType(rs.getString("source_type"));
        value.setSourceUrl(rs.getString("source_url"));
        value.setOriginalFileName(rs.getString("original_file_name"));
        value.setRelativePath(rs.getString("relative_path"));
        Object size = rs.getObject("file_size");
        value.setFileSize(size == null ? null : rs.getLong("file_size"));
        value.setFileHash(rs.getString("file_hash"));
        Object pages = rs.getObject("page_count");
        value.setPageCount(pages == null ? null : rs.getInt("page_count"));
        value.setParseStatus(rs.getString("parse_status"));
        value.setAnalysisStatus(rs.getString("analysis_status"));
        value.setQualityLevel(rs.getString("quality_level"));
        value.setErrorMessage(rs.getString("error_message"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    }

    private String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private BigDecimal decimal(String value) {
        return value == null || value.trim().isEmpty() ? null : new BigDecimal(value);
    }
}
