package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryModelPrediction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
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

    public Optional<StockDiscoveryRun> findLatestSuccessOnOrBefore(LocalDate businessDate) {
        List<StockDiscoveryRun> values = jdbcTemplate.query(
                "SELECT * FROM stock_discovery_run WHERE status='SUCCEEDED' AND business_date<=? "
                        + "ORDER BY business_date DESC,id DESC LIMIT 1", this::map, businessDate.toString());
        return values.stream().findFirst();
    }

    public List<StockDiscoveryRun> findRecent(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT * FROM stock_discovery_run ORDER BY id DESC LIMIT ?", this::map, bounded);
    }

    public boolean tryMarkRunning(Long id, String attemptToken) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE stock_discovery_run "
                        + "SET status='RUNNING',started_at=?,completed_at=NULL,error_message=NULL,attempt_token=? "
                        + "WHERE id=? AND (status IN ('CREATED','FAILED') "
                        + "OR (status='RUNNING' AND started_at<?))",
                TimeUtil.text(now), attemptToken, id, TimeUtil.text(now.minusMinutes(30)));
        return updated == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, String attemptToken, StockDiscoveryReport report) {
        jdbcTemplate.update("DELETE FROM stock_discovery_sector WHERE run_id=?", id);
        jdbcTemplate.update("DELETE FROM stock_discovery_model_prediction WHERE run_id=?", id);
        jdbcTemplate.update("DELETE FROM stock_discovery_candidate WHERE run_id=?", id);
        persistSectors(id, report.getSectors());
        persistCandidates(id, report);
        StockDiscoveryReport.Funnel funnel = report.getFunnel();
        int updated = jdbcTemplate.update("UPDATE stock_discovery_run SET status='SUCCEEDED',as_of_date=?,source_family=?,"
                        + "quality_status=?,data_fingerprint=?,sector_count=?,constituent_count=?,admitted_count=?,"
                        + "deep_review_count=?,final_count=?,report_json=?,completed_at=? "
                        + "WHERE id=? AND status='RUNNING' AND attempt_token=?",
                report.getAsOfDate(), report.getSourceFamily(), report.getQualityStatus(), report.getDataFingerprint(),
                report.getSectors().size(), funnel.getConstituentCount(), funnel.getAdmittedCount(),
                funnel.getDeepReviewCount(), funnel.getFinalCount(), report.getRawJson(),
                TimeUtil.text(LocalDateTime.now()), id, attemptToken);
        if (updated != 1) {
            throw new IllegalStateException("股票发现批次状态已变化，拒绝覆盖终态");
        }
    }

    public void fail(Long id, String attemptToken, String message) {
        jdbcTemplate.update("UPDATE stock_discovery_run SET status='FAILED',error_message=?,completed_at=? "
                        + "WHERE id=? AND status='RUNNING' AND attempt_token=?",
                message, TimeUtil.text(LocalDateTime.now()), id, attemptToken);
    }

    public List<StockDiscoveryCandidate> findPendingCandidates(int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        return jdbcTemplate.query("SELECT c.*,r.as_of_date AS evaluation_as_of_date "
                        + "FROM stock_discovery_candidate c JOIN stock_discovery_run r ON r.id=c.run_id "
                        + "WHERE c.maturity_status='PENDING' ORDER BY c.run_id,c.id LIMIT ?",
                this::mapCandidate, bounded);
    }

    public List<StockDiscoveryCandidate> findCandidatesByRunId(Long runId) {
        return jdbcTemplate.query("SELECT c.*,r.as_of_date AS evaluation_as_of_date "
                        + "FROM stock_discovery_candidate c JOIN stock_discovery_run r ON r.id=c.run_id "
                        + "WHERE c.run_id=? ORDER BY CASE WHEN c.final_rank IS NULL THEN 1 ELSE 0 END,"
                        + "c.final_rank,c.lightweight_rank,c.id",
                this::mapCandidate, runId);
    }

    public int countPendingCandidates() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_discovery_candidate WHERE maturity_status='PENDING'", Integer.class);
        return value == null ? 0 : value;
    }

    public List<StockDiscoveryCandidate> findMaturedCandidates(int limit) {
        int bounded = Math.max(1, Math.min(limit, 5000));
        return jdbcTemplate.query("SELECT c.*,r.as_of_date AS evaluation_as_of_date "
                        + "FROM stock_discovery_candidate c JOIN stock_discovery_run r ON r.id=c.run_id "
                        + "WHERE c.maturity_status='MATURED' ORDER BY c.run_id DESC,c.id DESC LIMIT ?",
                this::mapCandidate, bounded);
    }

    public List<StockDiscoveryModelPrediction> findMaturedModelPredictions(int limit) {
        int bounded = Math.max(1, Math.min(limit, 20000));
        return jdbcTemplate.query("SELECT p.*,r.as_of_date AS evaluation_as_of_date,"
                        + "r.data_fingerprint AS evaluation_data_fingerprint "
                        + "FROM stock_discovery_model_prediction p "
                        + "JOIN stock_discovery_run r ON r.id=p.run_id "
                        + "WHERE p.maturity_status='MATURED' ORDER BY p.run_id DESC,p.id DESC LIMIT ?",
                this::mapModelPrediction, bounded);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean settleCandidate(StockDiscoveryCandidate candidate, LocalDate entryDate, double entryOpen,
                                   LocalDate exitDate, double exitOpen, double actualNetReturn,
                                   String actualDirection, Boolean predictionCorrect,
                                   LocalDateTime settledAt, String sourceCode) {
        int updated = jdbcTemplate.update("UPDATE stock_discovery_candidate SET maturity_status='MATURED',"
                        + "entry_date=?,entry_open=?,exit_date=?,exit_open=?,actual_net_return=?,"
                        + "actual_direction=?,prediction_correct=?,settled_at=?,outcome_source_code=?,outcome_note=NULL "
                        + "WHERE id=? AND maturity_status='PENDING'",
                entryDate.toString(), entryOpen, exitDate.toString(), exitOpen, actualNetReturn,
                actualDirection, predictionCorrect == null ? null : predictionCorrect ? 1 : 0,
                TimeUtil.text(settledAt), sourceCode, candidate.getId());
        if (updated != 1) {
            return false;
        }
        jdbcTemplate.update("UPDATE stock_discovery_model_prediction SET maturity_status='MATURED',"
                        + "actual_net_return=?,actual_direction=?,"
                        + "prediction_correct=CASE WHEN shadow_decision=? THEN 1 ELSE 0 END,settled_at=? "
                        + "WHERE run_id=? AND instrument_code=? AND maturity_status='PENDING'",
                actualNetReturn, actualDirection, actualDirection, TimeUtil.text(settledAt),
                candidate.getRunId(), candidate.getInstrumentCode());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markCandidateUnavailable(StockDiscoveryCandidate candidate, LocalDateTime settledAt, String note) {
        int updated = jdbcTemplate.update("UPDATE stock_discovery_candidate SET maturity_status='UNAVAILABLE',"
                        + "settled_at=?,outcome_note=? WHERE id=? AND maturity_status='PENDING'",
                TimeUtil.text(settledAt), note, candidate.getId());
        if (updated != 1) {
            return false;
        }
        jdbcTemplate.update("UPDATE stock_discovery_model_prediction SET maturity_status='UNAVAILABLE',settled_at=? "
                        + "WHERE run_id=? AND instrument_code=? AND maturity_status='PENDING'",
                TimeUtil.text(settledAt), candidate.getRunId(), candidate.getInstrumentCode());
        return true;
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
        for (Map<String, Object> selected : report.getFinalCandidates()) {
            String code = text(selected, "code");
            Map<String, Object> merged = new HashMap<>();
            if (evidenceByCode.get(code) != null) {
                merged.putAll(evidenceByCode.get(code));
            }
            merged.putAll(selected);
            evidenceByCode.put(code, merged);
        }
        for (Map<String, Object> candidate : report.getCandidates()) {
            String code = text(candidate, "code");
            Map<String, Object> evidence = evidenceByCode.get(code);
            Integer finalRank = integer(evidence, "final_rank");
            boolean finalSelected = finalRank != null && finalRank > 0;
            Map<String, Object> detail = evidence == null
                    ? Map.of("candidate", candidate)
                    : Map.of("candidate", candidate, "deep_evidence", evidence);
            jdbcTemplate.update("INSERT INTO stock_discovery_candidate("
                            + "run_id,instrument_code,name,price,lot_cost,admitted,rejection_reasons_json,"
                            + "sector_codes_json,sector_names_json,lightweight_score,lightweight_rank,deep_score,"
                            + "final_rank,conclusion,calibrated_probability,health_status,detail_json,horizon_days,"
                            + "maturity_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", runId,
                    code + "." + text(candidate, "market"), text(candidate, "name"), decimal(candidate, "price"),
                    decimal(candidate, "lot_cost"), truth(candidate, "admitted") ? 1 : 0,
                    json(candidate.get("rejection_reasons")), json(candidate.get("sector_codes")),
                    json(candidate.get("sector_names")), decimal(candidate, "lightweight_score"),
                    integer(candidate, "lightweight_rank"), decimal(evidence, "deep_score"),
                    finalSelected ? finalRank : null, text(evidence, "conclusion"),
                    decimal(evidence, "calibrated_probability"), text(evidence, "health_status"), json(detail),
                    5, truth(candidate, "admitted") ? "PENDING" : "NOT_APPLICABLE");
            if (truth(candidate, "admitted")) {
                persistModelPredictions(runId, code + "." + text(candidate, "market"), evidence);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void persistModelPredictions(Long runId, String instrumentCode, Map<String, Object> evidence) {
        Map<String, Object> forecast = nested(evidence, "forecast_report");
        Map<String, Object> competition = nested(forecast, "modelCompetition");
        if (competition == null) {
            competition = nested(forecast, "model_competition");
        }
        Object rawCandidates = competition == null ? null : competition.get("candidates");
        if (!(rawCandidates instanceof List<?>)) {
            return;
        }
        for (Object rawCandidate : (List<?>) rawCandidates) {
            if (!(rawCandidate instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> model = (Map<String, Object>) rawCandidate;
            String modelCode = text(model, "code");
            String modelName = text(model, "name");
            String role = text(model, "role");
            String version = firstText(model, "modelVersion", "model_version");
            Double probability = firstDecimal(model, "calibratedProbability", "calibrated_probability");
            String decision = firstText(model, "shadowDecision", "shadow_decision");
            String qualification = firstText(model, "qualificationStatus", "qualification_status");
            if (modelCode == null || modelName == null || role == null || version == null
                    || probability == null || decision == null || qualification == null) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO stock_discovery_model_prediction("
                            + "run_id,instrument_code,horizon_days,model_code,model_name,model_version,role,"
                            + "calibrated_probability,shadow_decision,qualification_status,maturity_status) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING')",
                    runId, instrumentCode, 5, modelCode, modelName, version, role,
                    probability, decision, qualification);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Map<?, ?>)) {
            return null;
        }
        return (Map<String, Object>) source.get(key);
    }

    private String firstText(Map<String, Object> source, String first, String second) {
        String value = text(source, first);
        return value == null ? text(source, second) : value;
    }

    private Double firstDecimal(Map<String, Object> source, String first, String second) {
        Double value = decimal(source, first);
        return value == null ? decimal(source, second) : value;
    }

    private String text(Map<String, Object> source, String key) {
        if (source == null || source.get(key) == null) {
            return null;
        }
        return String.valueOf(source.get(key));
    }

    private Double decimal(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Number)) {
            return null;
        }
        return ((Number) source.get(key)).doubleValue();
    }

    private Integer integer(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Number)) {
            return null;
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

    private StockDiscoveryCandidate mapCandidate(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        StockDiscoveryCandidate value = new StockDiscoveryCandidate();
        value.setId(rs.getLong("id"));
        value.setRunId(rs.getLong("run_id"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setName(rs.getString("name"));
        value.setPrice(rs.getDouble("price"));
        value.setLotCost(rs.getDouble("lot_cost"));
        value.setAdmitted(rs.getInt("admitted") == 1);
        value.setRejectionReasonsJson(rs.getString("rejection_reasons_json"));
        value.setSectorCodesJson(rs.getString("sector_codes_json"));
        value.setSectorNamesJson(rs.getString("sector_names_json"));
        value.setLightweightScore(nullableDouble(rs, "lightweight_score"));
        value.setLightweightRank(nullableInteger(rs, "lightweight_rank"));
        value.setDeepScore(nullableDouble(rs, "deep_score"));
        value.setFinalRank(nullableInteger(rs, "final_rank"));
        value.setConclusion(rs.getString("conclusion"));
        value.setCalibratedProbability(nullableDouble(rs, "calibrated_probability"));
        value.setHealthStatus(rs.getString("health_status"));
        value.setDetailJson(rs.getString("detail_json"));
        value.setAsOfDate(localDate(rs.getString("evaluation_as_of_date")));
        value.setHorizonDays(rs.getInt("horizon_days"));
        value.setMaturityStatus(rs.getString("maturity_status"));
        value.setEntryDate(localDate(rs.getString("entry_date")));
        value.setExitDate(localDate(rs.getString("exit_date")));
        value.setEntryOpen(nullableDouble(rs, "entry_open"));
        value.setExitOpen(nullableDouble(rs, "exit_open"));
        value.setActualNetReturn(nullableDouble(rs, "actual_net_return"));
        value.setActualDirection(rs.getString("actual_direction"));
        value.setPredictionCorrect(nullableBoolean(rs, "prediction_correct"));
        value.setSettledAt(TimeUtil.localDateTime(rs, "settled_at"));
        value.setOutcomeSourceCode(rs.getString("outcome_source_code"));
        value.setOutcomeNote(rs.getString("outcome_note"));
        return value;
    }

    private StockDiscoveryModelPrediction mapModelPrediction(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        StockDiscoveryModelPrediction value = new StockDiscoveryModelPrediction();
        value.setId(rs.getLong("id"));
        value.setRunId(rs.getLong("run_id"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setAsOfDate(localDate(rs.getString("evaluation_as_of_date")));
        value.setHorizonDays(rs.getInt("horizon_days"));
        value.setDataFingerprint(rs.getString("evaluation_data_fingerprint"));
        value.setModelCode(rs.getString("model_code"));
        value.setModelName(rs.getString("model_name"));
        value.setModelVersion(rs.getString("model_version"));
        value.setRole(rs.getString("role"));
        value.setCalibratedProbability(rs.getDouble("calibrated_probability"));
        value.setShadowDecision(rs.getString("shadow_decision"));
        value.setQualificationStatus(rs.getString("qualification_status"));
        value.setMaturityStatus(rs.getString("maturity_status"));
        value.setActualNetReturn(nullableDouble(rs, "actual_net_return"));
        value.setActualDirection(rs.getString("actual_direction"));
        value.setPredictionCorrect(nullableBoolean(rs, "prediction_correct"));
        value.setSettledAt(TimeUtil.localDateTime(rs, "settled_at"));
        return value;
    }

    private LocalDate localDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value == 1;
    }
}
