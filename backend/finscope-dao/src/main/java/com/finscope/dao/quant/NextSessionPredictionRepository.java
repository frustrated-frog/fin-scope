package com.finscope.dao.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.quant.NextSessionOutcomeStatus;
import com.finscope.domain.quant.forecast.NextSessionPrediction;
import com.finscope.domain.quant.forecast.NextSessionPredictionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Imports only immutable, genuinely forward reports; never regenerates historical forecasts. */
@Repository
public class NextSessionPredictionRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper json;

    @Transactional
    public int importFrozenReports() {
        String single = "SELECT instrument_code AS code,json_extract(report_json,'$.nextSession') AS prediction "
                + "FROM single_stock_forecast_run WHERE json_valid(report_json) "
                + "AND json_type(report_json,'$.nextSession')='object'";
        String discovery = "SELECT json_extract(e.value,'$.forecast_report.instrumentCode') AS code,"
                + "json_extract(e.value,'$.forecast_report.nextSession') AS prediction "
                + "FROM stock_discovery_run r,json_each(r.report_json,'$.deep_evidence') e "
                + "WHERE r.status='SUCCEEDED' AND json_valid(r.report_json) "
                + "AND json_type(e.value,'$.forecast_report.nextSession')='object'";
        return importSource(single) + importSource(discovery);
    }

    private int importSource(String sourceQuery) {
        // Both source queries are internal constants, not request-controlled SQL.
        return jdbcTemplate.update("INSERT OR IGNORE INTO next_session_prediction("
                + "instrument_code,as_of_date,target_date,data_fingerprint,prediction_json) "
                + "SELECT code,json_extract(prediction,'$.asOfDate'),json_extract(prediction,'$.targetDate'),"
                + "json_extract(prediction,'$.dataFingerprint'),prediction FROM (" + sourceQuery + ") source "
                + "WHERE code IS NOT NULL AND json_extract(prediction,'$.status') IN ('READY','WATCH') "
                + "AND json_extract(prediction,'$.label')='NEXT_CLOSE_RETURN' "
                + "AND json_extract(prediction,'$.targetDate')>substr(json_extract(prediction,'$.generatedAt'),1,10) "
                + "AND json_extract(prediction,'$.targetDate')>json_extract(prediction,'$.asOfDate') "
                + "AND length(json_extract(prediction,'$.dataFingerprint'))=64 "
                + "AND json_extract(prediction,'$.upProbability') BETWEEN 0 AND 1 "
                + "AND NOT EXISTS (SELECT 1 FROM next_session_prediction n WHERE n.instrument_code=code "
                + "AND n.as_of_date=json_extract(prediction,'$.asOfDate') "
                + "AND n.data_fingerprint=json_extract(prediction,'$.dataFingerprint')) LIMIT 200");
    }

    public List<NextSessionPredictionRecord> findPending(int limit) {
        return jdbcTemplate.query("SELECT * FROM next_session_prediction WHERE status='PENDING' "
                + "ORDER BY target_date,id LIMIT ?", this::map, Math.max(1, Math.min(limit, 200)));
    }

    public List<NextSessionPredictionRecord> history(String code, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        if (code == null) {
            return jdbcTemplate.query("SELECT * FROM next_session_prediction ORDER BY as_of_date DESC,id DESC LIMIT ?",
                    this::map, bounded);
        }
        return jdbcTemplate.query("SELECT * FROM next_session_prediction WHERE substr(instrument_code,1,6)=? "
                + "ORDER BY as_of_date DESC,id DESC LIMIT ?", this::map, code, bounded);
    }

    public boolean settle(Long id, double actualReturn, boolean correct, boolean covered,
                          LocalDateTime at, String source) {
        if (!Double.isFinite(actualReturn)) {
            throw new IllegalArgumentException("次日验证收益必须是有限数值");
        }
        return jdbcTemplate.update("UPDATE next_session_prediction SET status='MATURED',actual_return=?,"
                + "prediction_correct=?,interval_covered=?,settled_at=?,outcome_note=? WHERE id=? AND status='PENDING'",
                actualReturn, correct ? 1 : 0, covered ? 1 : 0, at.toString(), source, id) == 1;
    }

    public boolean unavailable(Long id, LocalDateTime at, String reason) {
        return jdbcTemplate.update("UPDATE next_session_prediction SET status='UNAVAILABLE',settled_at=?,"
                + "outcome_note=? WHERE id=? AND status='PENDING'", at.toString(), reason, id) == 1;
    }

    private NextSessionPredictionRecord map(ResultSet row, int index) throws SQLException {
        NextSessionPredictionRecord value = new NextSessionPredictionRecord();
        value.setId(row.getLong("id"));
        value.setInstrumentCode(row.getString("instrument_code"));
        value.setStatus(NextSessionOutcomeStatus.valueOf(row.getString("status")));
        try {
            value.setPrediction(json.readValue(row.getString("prediction_json"), NextSessionPrediction.class));
        } catch (Exception error) {
            throw new IllegalStateException("冻结次日预测无法读取，id=" + value.getId(), error);
        }
        if (row.getObject("actual_return") != null) {
            value.setActualReturn(row.getDouble("actual_return"));
            value.setCorrect(row.getInt("prediction_correct") == 1);
            value.setIntervalCovered(row.getInt("interval_covered") == 1);
        }
        if (row.getString("settled_at") != null) {
            value.setSettledAt(LocalDateTime.parse(row.getString("settled_at")));
        }
        value.setOutcomeNote(row.getString("outcome_note"));
        return value;
    }
}
