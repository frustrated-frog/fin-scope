package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.forecast.ForecastCandidateRun;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ForecastCandidateRunRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ForecastCandidateRun> mapper = (rs, rowNum) -> {
        ForecastCandidateRun value = new ForecastCandidateRun();
        value.setId(rs.getLong("id"));
        value.setForecastRunId(rs.getLong("forecast_run_id"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setAsOfDate(LocalDate.parse(rs.getString("as_of_date")));
        value.setHorizonDays(rs.getInt("horizon_days"));
        value.setDataFingerprint(rs.getString("data_fingerprint"));
        value.setModelCode(rs.getString("model_code"));
        value.setModelName(rs.getString("model_name"));
        value.setModelVersion(rs.getString("model_version"));
        value.setRole(rs.getString("role"));
        value.setRawProbability(nullableDouble(rs, "raw_probability"));
        value.setCalibratedProbability(nullableDouble(rs, "calibrated_probability"));
        value.setShadowDecision(rs.getString("shadow_decision"));
        value.setQualificationStatus(rs.getString("qualification_status"));
        value.setLockedSampleCount(rs.getInt("locked_sample_count"));
        value.setLockedAccuracy(rs.getDouble("locked_accuracy"));
        value.setLockedBrierScore(rs.getDouble("locked_brier_score"));
        value.setLockedLogLoss(rs.getDouble("locked_log_loss"));
        value.setLockedBrierSkillScore(rs.getDouble("locked_brier_skill_score"));
        value.setMaturityStatus(rs.getString("maturity_status"));
        value.setActualNetReturn(nullableDouble(rs, "actual_net_return"));
        value.setActualDirection(rs.getString("actual_direction"));
        int correct = rs.getInt("prediction_correct");
        value.setPredictionCorrect(rs.wasNull() ? null : correct == 1);
        value.setSettledAt(TimeUtil.localDateTime(rs, "settled_at"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public void saveAll(Long forecastRunId, List<ForecastCandidateRun> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        List<SingleStockForecastRun> runValues = jdbcTemplate.query(
                "SELECT instrument_code,as_of_date,horizon_days,data_fingerprint "
                        + "FROM single_stock_forecast_run WHERE id=?",
                (rs, rowNum) -> {
                    SingleStockForecastRun value = new SingleStockForecastRun();
                    value.setInstrumentCode(rs.getString("instrument_code"));
                    value.setAsOfDate(LocalDate.parse(rs.getString("as_of_date")));
                    value.setHorizonDays(rs.getInt("horizon_days"));
                    value.setDataFingerprint(rs.getString("data_fingerprint"));
                    return value;
                }, forecastRunId);
        if (runValues.isEmpty()) {
            throw new IllegalArgumentException("预测运行不存在");
        }
        SingleStockForecastRun run = runValues.get(0);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate("INSERT INTO single_stock_forecast_candidate_run("
                        + "forecast_run_id,instrument_code,as_of_date,horizon_days,data_fingerprint,model_code,"
                        + "model_name,model_version,role,raw_probability,calibrated_probability,shadow_decision,"
                        + "qualification_status,locked_sample_count,locked_accuracy,locked_brier_score,"
                        + "locked_log_loss,locked_brier_skill_score,maturity_status,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                candidates, candidates.size(), (PreparedStatement statement, ForecastCandidateRun value) -> {
                    statement.setLong(1, forecastRunId);
                    statement.setString(2, run.getInstrumentCode());
                    statement.setString(3, run.getAsOfDate().toString());
                    statement.setInt(4, run.getHorizonDays());
                    statement.setString(5, run.getDataFingerprint());
                    statement.setString(6, value.getModelCode());
                    statement.setString(7, value.getModelName());
                    statement.setString(8, value.getModelVersion());
                    statement.setString(9, value.getRole());
                    statement.setDouble(10, value.getRawProbability());
                    statement.setDouble(11, value.getCalibratedProbability());
                    statement.setString(12, value.getShadowDecision());
                    statement.setString(13, value.getQualificationStatus());
                    statement.setInt(14, value.getLockedSampleCount());
                    statement.setDouble(15, value.getLockedAccuracy());
                    statement.setDouble(16, value.getLockedBrierScore());
                    statement.setDouble(17, value.getLockedLogLoss());
                    statement.setDouble(18, value.getLockedBrierSkillScore());
                    statement.setString(19, value.getMaturityStatus());
                    statement.setString(20, TimeUtil.text(now));
                });
    }

    public List<ForecastCandidateRun> findByForecastRunId(Long forecastRunId) {
        return jdbcTemplate.query("SELECT * FROM single_stock_forecast_candidate_run "
                        + "WHERE forecast_run_id=? ORDER BY model_code", mapper, forecastRunId);
    }

    public int settleByForecastRunId(Long forecastRunId, double actualNetReturn,
                                     String actualDirection, LocalDateTime settledAt) {
        return jdbcTemplate.update("UPDATE single_stock_forecast_candidate_run SET maturity_status='MATURED',"
                        + "actual_net_return=?,actual_direction=?,prediction_correct=CASE "
                        + "WHEN shadow_decision IN ('UP','DOWN') THEN CASE WHEN shadow_decision=? THEN 1 ELSE 0 END "
                        + "ELSE NULL END,settled_at=? WHERE forecast_run_id=? AND maturity_status='PENDING'",
                actualNetReturn, actualDirection, actualDirection, TimeUtil.text(settledAt), forecastRunId);
    }

    public int markUnavailableByForecastRunId(Long forecastRunId, LocalDateTime settledAt) {
        return jdbcTemplate.update("UPDATE single_stock_forecast_candidate_run "
                        + "SET maturity_status='UNAVAILABLE',settled_at=? "
                        + "WHERE forecast_run_id=? AND maturity_status='PENDING'",
                TimeUtil.text(settledAt), forecastRunId);
    }

    public List<ForecastCandidateRun> findMaturedEvidence(String instrumentCode, int horizonDays,
                                                           int limit) {
        int bounded = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query("SELECT candidate.* FROM single_stock_forecast_candidate_run candidate "
                        + "WHERE candidate.instrument_code=? AND candidate.horizon_days=? "
                        + "AND candidate.maturity_status='MATURED' AND candidate.forecast_run_id IN ("
                        + "SELECT MIN(first.forecast_run_id) FROM single_stock_forecast_candidate_run first "
                        + "WHERE first.instrument_code=candidate.instrument_code AND first.horizon_days=candidate.horizon_days "
                        + "AND first.maturity_status='MATURED' GROUP BY first.data_fingerprint "
                        + "ORDER BY MIN(first.as_of_date) DESC LIMIT ?) ORDER BY candidate.as_of_date,candidate.model_code",
                mapper, instrumentCode, horizonDays, bounded);
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
