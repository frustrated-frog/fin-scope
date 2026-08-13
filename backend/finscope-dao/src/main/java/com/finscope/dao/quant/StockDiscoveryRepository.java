package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StockDiscoveryRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public StockDiscoveryRun createIfAbsent(String runKey, LocalDate businessDate, double budget,
                                            String policyVersion, String triggerType) {
        jdbcTemplate.update("INSERT OR IGNORE INTO stock_discovery_run("
                        + "run_key,business_date,trigger_type,status,budget,policy_version,created_at) "
                        + "VALUES(?,?,?,'CREATED',?,?,?)", runKey, businessDate.toString(), triggerType,
                budget, policyVersion, TimeUtil.text(LocalDateTime.now()));
        return findByKey(runKey).orElseThrow(() -> new IllegalStateException("股票发现批次创建失败"));
    }

    public Optional<StockDiscoveryRun> findByKey(String runKey) {
        List<StockDiscoveryRun> values = jdbcTemplate.query(
                "SELECT * FROM stock_discovery_run WHERE run_key=?", this::map, runKey);
        return values.stream().findFirst();
    }

    public Optional<StockDiscoveryRun> findById(Long id) {
        List<StockDiscoveryRun> values = jdbcTemplate.query(
                "SELECT * FROM stock_discovery_run WHERE id=?", this::map, id);
        return values.stream().findFirst();
    }

    public Optional<StockDiscoveryRun> findLatestSuccess() {
        List<StockDiscoveryRun> values = jdbcTemplate.query(
                "SELECT * FROM stock_discovery_run WHERE status='SUCCEEDED' "
                        + "ORDER BY business_date DESC,id DESC LIMIT 1", this::map);
        return values.stream().findFirst();
    }

    public List<StockDiscoveryRun> findRecent(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT * FROM stock_discovery_run ORDER BY id DESC LIMIT ?", this::map, bounded);
    }

    public void markRunning(Long id) {
        jdbcTemplate.update("UPDATE stock_discovery_run SET status='RUNNING',started_at=?,error_message=NULL WHERE id=?",
                TimeUtil.text(LocalDateTime.now()), id);
    }

    public void complete(Long id, String asOfDate, String sourceFamily, String qualityStatus,
                         String dataFingerprint, int sectorCount, int constituentCount, int admittedCount,
                         int deepReviewCount, int finalCount, String reportJson) {
        jdbcTemplate.update("UPDATE stock_discovery_run SET status='SUCCEEDED',as_of_date=?,source_family=?,"
                        + "quality_status=?,data_fingerprint=?,sector_count=?,constituent_count=?,admitted_count=?,"
                        + "deep_review_count=?,final_count=?,report_json=?,completed_at=? WHERE id=?",
                asOfDate, sourceFamily, qualityStatus, dataFingerprint, sectorCount, constituentCount,
                admittedCount, deepReviewCount, finalCount, reportJson, TimeUtil.text(LocalDateTime.now()), id);
    }

    public void fail(Long id, String message) {
        jdbcTemplate.update("UPDATE stock_discovery_run SET status='FAILED',error_message=?,completed_at=? WHERE id=?",
                message, TimeUtil.text(LocalDateTime.now()), id);
    }

    private StockDiscoveryRun map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        StockDiscoveryRun value = new StockDiscoveryRun();
        value.setId(rs.getLong("id"));
        value.setRunKey(rs.getString("run_key"));
        value.setBusinessDate(LocalDate.parse(rs.getString("business_date")));
        value.setTriggerType(rs.getString("trigger_type"));
        value.setStatus(rs.getString("status"));
        value.setBudget(rs.getDouble("budget"));
        value.setPolicyVersion(rs.getString("policy_version"));
        value.setAsOfDate(rs.getString("as_of_date"));
        value.setSourceFamily(rs.getString("source_family"));
        value.setQualityStatus(rs.getString("quality_status"));
        value.setDataFingerprint(rs.getString("data_fingerprint"));
        value.setSectorCount(rs.getInt("sector_count"));
        value.setConstituentCount(rs.getInt("constituent_count"));
        value.setAdmittedCount(rs.getInt("admitted_count"));
        value.setDeepReviewCount(rs.getInt("deep_review_count"));
        value.setFinalCount(rs.getInt("final_count"));
        value.setReportJson(rs.getString("report_json"));
        value.setErrorMessage(rs.getString("error_message"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at"));
        return value;
    }
}
