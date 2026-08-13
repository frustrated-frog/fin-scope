package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class StockDiscoveryRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper objectMapper;

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

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, StockDiscoveryReport report) {
        jdbcTemplate.update("DELETE FROM stock_discovery_sector WHERE run_id=?", id);
        jdbcTemplate.update("DELETE FROM stock_discovery_candidate WHERE run_id=?", id);
        persistSectors(id, report.getSectors());
        persistCandidates(id, report);
        StockDiscoveryReport.Funnel funnel = report.getFunnel();
        jdbcTemplate.update("UPDATE stock_discovery_run SET status='SUCCEEDED',as_of_date=?,source_family=?,"
                        + "quality_status=?,data_fingerprint=?,sector_count=?,constituent_count=?,admitted_count=?,"
                        + "deep_review_count=?,final_count=?,report_json=?,completed_at=? WHERE id=?",
                report.getAsOfDate(), report.getSourceFamily(), report.getQualityStatus(), report.getDataFingerprint(),
                report.getSectors().size(), funnel.getConstituentCount(), funnel.getAdmittedCount(),
                funnel.getDeepReviewCount(), funnel.getFinalCount(), report.getRawJson(),
                TimeUtil.text(LocalDateTime.now()), id);
    }

    public void fail(Long id, String message) {
        jdbcTemplate.update("UPDATE stock_discovery_run SET status='FAILED',error_message=?,completed_at=? WHERE id=?",
                message, TimeUtil.text(LocalDateTime.now()), id);
    }

    private void persistSectors(Long runId, List<Map<String, Object>> sectors) {
        for (Map<String, Object> sector : sectors) {
            jdbcTemplate.update("INSERT INTO stock_discovery_sector("
                            + "run_id,sector_code,sector_name,category,source_code,source_family,period,source_rank,"
                            + "change_pct,main_net_inflow,main_net_inflow_ratio,leader_stock_name,retrieved_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", runId, text(sector, "code"), text(sector, "name"),
                    text(sector, "category"), text(sector, "source_code"), text(sector, "source_family"),
                    text(sector, "period"), integer(sector, "source_rank"), decimal(sector, "change_pct"),
                    decimal(sector, "main_net_inflow"), decimal(sector, "main_net_inflow_ratio"),
                    text(sector, "leader_stock_name"), text(sector, "retrieved_at"));
        }
    }

    private void persistCandidates(Long runId, StockDiscoveryReport report) {
        Map<String, Map<String, Object>> evidenceByCode = report.getDeepEvidence().stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> text(value, "code"), value -> value, (left, right) -> left));
        for (Map<String, Object> candidate : report.getCandidates()) {
            String code = text(candidate, "code");
            Map<String, Object> evidence = evidenceByCode.get(code);
            boolean finalSelected = evidence != null && integer(evidence, "final_rank") > 0;
            Map<String, Object> detail = evidence == null
                    ? Map.of("candidate", candidate)
                    : Map.of("candidate", candidate, "deep_evidence", evidence);
            jdbcTemplate.update("INSERT INTO stock_discovery_candidate("
                            + "run_id,instrument_code,name,price,lot_cost,admitted,rejection_reasons_json,"
                            + "sector_codes_json,sector_names_json,lightweight_score,lightweight_rank,deep_score,"
                            + "final_rank,conclusion,calibrated_probability,health_status,detail_json) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", runId,
                    code + "." + text(candidate, "market"), text(candidate, "name"), decimal(candidate, "price"),
                    decimal(candidate, "lot_cost"), truth(candidate, "admitted") ? 1 : 0,
                    json(candidate.get("rejection_reasons")), json(candidate.get("sector_codes")),
                    json(candidate.get("sector_names")), decimal(candidate, "lightweight_score"),
                    integer(candidate, "lightweight_rank"), decimal(evidence, "deep_score"),
                    finalSelected ? integer(evidence, "final_rank") : null, text(evidence, "conclusion"),
                    decimal(evidence, "calibrated_probability"), text(evidence, "health_status"), json(detail));
        }
    }

    private String text(Map<String, Object> source, String key) {
        if (source == null || source.get(key) == null) {
            return null;
        }
        return String.valueOf(source.get(key));
    }

    private double decimal(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Number)) {
            return 0d;
        }
        return ((Number) source.get(key)).doubleValue();
    }

    private int integer(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Number)) {
            return 0;
        }
        return ((Number) source.get(key)).intValue();
    }

    private boolean truth(Map<String, Object> source, String key) {
        return source != null && Boolean.TRUE.equals(source.get(key));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("股票发现明细序列化失败", error);
        }
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
