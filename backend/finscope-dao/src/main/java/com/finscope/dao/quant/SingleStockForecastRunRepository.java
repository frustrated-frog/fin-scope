package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SingleStockForecastRunRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<SingleStockForecastRun> mapper = (rs, rowNum) -> {
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setId(rs.getLong("id"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setAsOfDate(LocalDate.parse(rs.getString("as_of_date")));
        value.setHorizonDays(rs.getInt("horizon_days"));
        value.setStatus(rs.getString("status"));
        double probability = rs.getDouble("up_probability");
        value.setUpProbability(rs.wasNull() ? null : probability);
        value.setDataFingerprint(rs.getString("data_fingerprint"));
        value.setModelVersion(rs.getString("model_version"));
        value.setReportSchemaVersion(rs.getString("report_schema_version"));
        value.setSameDataAsPrevious(rs.getInt("same_data_as_previous") == 1);
        value.setMaturityStatus(SingleStockForecastRun.MaturityStatus.valueOf(
                rs.getString("maturity_status")));
        value.setReportJson(rs.getString("report_json"));
        value.setHoldingSnapshotJson(rs.getString("holding_snapshot_json"));
        if (rs.getString("settled_at") != null || rs.getString("outcome_note") != null) {
            SingleStockForecastRun.ForecastOutcome outcome = new SingleStockForecastRun.ForecastOutcome();
            outcome.setEntryDate(date(rs.getString("entry_date")));
            outcome.setExitDate(date(rs.getString("exit_date")));
            outcome.setEntryOpen(nullableDouble(rs, "entry_open"));
            outcome.setExitOpen(nullableDouble(rs, "exit_open"));
            outcome.setActualNetReturn(nullableDouble(rs, "actual_net_return"));
            outcome.setActualDirection(rs.getString("actual_direction"));
            int correct = rs.getInt("prediction_correct");
            outcome.setCorrect(rs.wasNull() ? null : correct == 1);
            outcome.setSettledAt(TimeUtil.localDateTime(rs, "settled_at"));
            outcome.setSourceCode(rs.getString("outcome_source_code"));
            outcome.setNote(rs.getString("outcome_note"));
            value.setOutcome(outcome);
        }
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public SingleStockForecastRun save(SingleStockForecastRun value) {
        List<String> previous = jdbcTemplate.query(
                "SELECT data_fingerprint FROM single_stock_forecast_run "
                        + "WHERE instrument_code=? AND horizon_days=? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1), value.getInstrumentCode(), value.getHorizonDays());
        value.setSameDataAsPrevious(!previous.isEmpty()
                && value.getDataFingerprint().equals(previous.get(0)));
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO single_stock_forecast_run(instrument_code,as_of_date,horizon_days,status,up_probability,"
                            + "data_fingerprint,model_version,report_schema_version,same_data_as_previous,"
                            + "maturity_status,report_json,holding_snapshot_json,created_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, value.getInstrumentCode());
            statement.setString(2, value.getAsOfDate().toString());
            statement.setInt(3, value.getHorizonDays());
            statement.setString(4, value.getStatus());
            if (value.getUpProbability() == null) {
                statement.setNull(5, java.sql.Types.REAL);
            } else {
                statement.setDouble(5, value.getUpProbability());
            }
            statement.setString(6, value.getDataFingerprint());
            statement.setString(7, value.getModelVersion());
            statement.setString(8, value.getReportSchemaVersion());
            statement.setInt(9, value.isSameDataAsPrevious() ? 1 : 0);
            statement.setString(10, value.getMaturityStatus().name());
            statement.setString(11, value.getReportJson());
            statement.setString(12, value.getHoldingSnapshotJson());
            statement.setString(13, TimeUtil.text(now));
            return statement;
        }, keys);
        value.setId(keys.getKey().longValue());
        return findById(value.getId()).orElse(value);
    }

    public Optional<SingleStockForecastRun> findById(Long id) {
        List<SingleStockForecastRun> values = jdbcTemplate.query(
                "SELECT * FROM single_stock_forecast_run WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<SingleStockForecastRun> findLatest(String instrumentCode) {
        List<SingleStockForecastRun> values = jdbcTemplate.query(
                "SELECT * FROM single_stock_forecast_run WHERE instrument_code=? "
                        + "ORDER BY as_of_date DESC,id DESC LIMIT 1",
                mapper, instrumentCode);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<SingleStockForecastRun> findAll(String instrumentCode, int limit) {
        return findAll(instrumentCode, limit, null);
    }

    public List<SingleStockForecastRun> findAll(String instrumentCode, int limit, Integer horizonDays) {
        int bounded = Math.max(1, Math.min(limit, 200));
        boolean withoutCode = instrumentCode == null || instrumentCode.trim().isEmpty();
        if (withoutCode && horizonDays == null) {
            return jdbcTemplate.query(
                    "SELECT * FROM single_stock_forecast_run ORDER BY id DESC LIMIT ?", mapper, bounded);
        }
        if (withoutCode) {
            return jdbcTemplate.query(
                    "SELECT * FROM single_stock_forecast_run WHERE horizon_days=? ORDER BY id DESC LIMIT ?",
                    mapper, horizonDays, bounded);
        }
        if (horizonDays != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM single_stock_forecast_run WHERE instrument_code=? AND horizon_days=? "
                            + "ORDER BY id DESC LIMIT ?",
                    mapper, instrumentCode, horizonDays, bounded);
        }
        return jdbcTemplate.query(
                "SELECT * FROM single_stock_forecast_run WHERE instrument_code=? ORDER BY id DESC LIMIT ?",
                mapper, instrumentCode, bounded);
    }

    public List<SingleStockForecastRun> findPending(String instrumentCode, int limit) {
        return jdbcTemplate.query("SELECT * FROM single_stock_forecast_run WHERE instrument_code=? "
                        + "AND maturity_status='PENDING' ORDER BY as_of_date,id LIMIT ?",
                mapper, instrumentCode, Math.max(1, Math.min(limit, 200)));
    }

    public List<SingleStockForecastRun> findHealthEvidence(String instrumentCode, int horizonDays,
                                                            String modelVersion, int limit) {
        return jdbcTemplate.query("SELECT current.* FROM single_stock_forecast_run current "
                        + "WHERE current.instrument_code=? AND current.horizon_days=? "
                        + "AND current.model_version=? AND current.maturity_status='MATURED' "
                        + "AND current.id=(SELECT MIN(first.id) FROM single_stock_forecast_run first "
                        + "WHERE first.instrument_code=current.instrument_code "
                        + "AND first.horizon_days=current.horizon_days "
                        + "AND first.model_version=current.model_version "
                        + "AND first.data_fingerprint=current.data_fingerprint "
                        + "AND first.maturity_status='MATURED') "
                        + "ORDER BY current.as_of_date DESC,current.id DESC LIMIT ?",
                mapper, instrumentCode, horizonDays, modelVersion, Math.max(1, Math.min(limit, 200)));
    }

    public boolean settle(Long id, SingleStockForecastRun.ForecastOutcome outcome) {
        return jdbcTemplate.update("UPDATE single_stock_forecast_run SET maturity_status='MATURED',"
                        + "entry_date=?,exit_date=?,entry_open=?,exit_open=?,actual_net_return=?,actual_direction=?,"
                        + "prediction_correct=?,settled_at=?,outcome_source_code=?,outcome_note=? "
                        + "WHERE id=? AND maturity_status='PENDING'",
                text(outcome.getEntryDate()), text(outcome.getExitDate()), outcome.getEntryOpen(),
                outcome.getExitOpen(), outcome.getActualNetReturn(), outcome.getActualDirection(),
                outcome.getCorrect() == null ? null : outcome.getCorrect() ? 1 : 0,
                TimeUtil.text(outcome.getSettledAt()), outcome.getSourceCode(), outcome.getNote(), id) == 1;
    }

    public boolean markUnavailable(Long id, String note) {
        return jdbcTemplate.update("UPDATE single_stock_forecast_run SET maturity_status='UNAVAILABLE',"
                        + "settled_at=?,outcome_note=? WHERE id=? AND maturity_status='PENDING'",
                TimeUtil.text(LocalDateTime.now()), note, id) == 1;
    }

    private static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static String text(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
