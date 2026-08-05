package com.finscope.dao.quant;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.backtest.BacktestTrade;
import com.finscope.domain.quant.backtest.EquityPoint;
import com.finscope.domain.quant.backtest.AnnualPerformance;
import com.finscope.domain.quant.backtest.PositionSnapshot;
import com.finscope.domain.quant.experiment.QuantExperiment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class QuantExperimentRepository {
    @Resource private JdbcTemplate jdbcTemplate;
    private final RowMapper<QuantExperiment> mapper = (rs, rowNum) -> {
        QuantExperiment value = new QuantExperiment(); value.setId(rs.getLong("id"));
        value.setStrategyVersionId(rs.getLong("strategy_version_id")); value.setRequestFingerprint(rs.getString("request_fingerprint"));
        value.setDatasetFingerprint(rs.getString("dataset_fingerprint")); value.setEngineVersion(rs.getString("engine_version"));
        value.setStatus(rs.getString("status")); value.setErrorMessage(rs.getString("error_message"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); value.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at")); return value;
    };

    public QuantExperiment save(QuantExperiment value) {
        LocalDateTime now = LocalDateTime.now(); KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO quant_experiment(strategy_version_id,request_fingerprint,"
                    + "dataset_fingerprint,engine_version,status,created_at) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, value.getStrategyVersionId()); ps.setString(2, value.getRequestFingerprint());
            ps.setString(3, value.getDatasetFingerprint()); ps.setString(4, value.getEngineVersion());
            ps.setString(5, value.getStatus()); ps.setString(6, TimeUtil.text(now)); return ps;
        }, keys); value.setId(keys.getKey().longValue()); return findById(value.getId()).orElse(value);
    }
    public Optional<QuantExperiment> findActiveByRequestFingerprint(String fingerprint) {
        List<QuantExperiment> values = jdbcTemplate.query("SELECT * FROM quant_experiment WHERE request_fingerprint=? "
                + "AND status IN ('QUEUED','RUNNING') ORDER BY id DESC LIMIT 1", mapper, fingerprint);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
    public Optional<QuantExperiment> findById(Long id) {
        List<QuantExperiment> values = jdbcTemplate.query("SELECT * FROM quant_experiment WHERE id=?", mapper, id);
        if (values.isEmpty()) return Optional.empty(); QuantExperiment value = values.get(0);
        if ("SUCCEEDED".equals(value.getStatus())) value.setResult(loadResult(id));
        List<String> interpretation = jdbcTemplate.query("SELECT content_json FROM quant_experiment_interpretation WHERE experiment_id=?",
                (rs, rowNum) -> rs.getString(1), id); if (!interpretation.isEmpty()) value.setInterpretation(interpretation.get(0));
        return Optional.of(value);
    }
    public List<QuantExperiment> findAll() { return jdbcTemplate.query("SELECT * FROM quant_experiment ORDER BY id DESC", mapper); }
    public boolean markRunning(Long id) { return jdbcTemplate.update("UPDATE quant_experiment SET status='RUNNING',started_at=? WHERE id=? AND status='QUEUED'",
            TimeUtil.text(LocalDateTime.now()), id) == 1; }
    public boolean markFailed(Long id, String message) { return jdbcTemplate.update("UPDATE quant_experiment SET status='FAILED',error_message=?,completed_at=? "
                    + "WHERE id=? AND status IN ('QUEUED','RUNNING')",
            message, TimeUtil.text(LocalDateTime.now()), id) == 1; }

    @Transactional
    public void complete(Long id, BacktestResult result) {
        if (jdbcTemplate.update("UPDATE quant_experiment SET status='SUCCEEDED',completed_at=? WHERE id=? AND status='RUNNING'",
                TimeUtil.text(LocalDateTime.now()), id) != 1) {
            throw new BusinessException(BizErrorCode.QUANT_EXPERIMENT_STATE_CHANGED,
                    BizErrorCode.QUANT_EXPERIMENT_STATE_CHANGED.format(id), null);
        }
        BacktestMetrics m = result.getMetrics(); metric(id, "TOTAL_RETURN", m.getTotalReturn()); metric(id, "ANNUAL_RETURN", m.getAnnualizedReturn());
        metric(id, "VOLATILITY", m.getAnnualizedVolatility()); metric(id, "MAX_DRAWDOWN", m.getMaxDrawdown());
        metric(id, "SHARPE", m.getSharpeRatio()); metric(id, "CALMAR", m.getCalmarRatio()); metric(id, "WIN_RATE", m.getWinRate());
        metric(id, "TURNOVER", m.getTurnover()); metric(id, "TRADE_COUNT", m.getTradeCount());
        metric(id, "BENCHMARK_RETURN", m.getBenchmarkReturn()); metric(id, "EXCESS_RETURN", m.getExcessReturn());
        saveEquityPoints(id, result.getEquityCurve());
        saveTrades(id, result.getTrades());
        saveWarnings(id, result.getWarnings());
        saveAnnual(id, result.getAnnualPerformance());
        savePositions(id, result.getPositions());
    }
    public void saveInterpretation(Long id, String json, String model) {
        jdbcTemplate.update("INSERT OR REPLACE INTO quant_experiment_interpretation(experiment_id,content_json,model,created_at) VALUES(?,?,?,?)",
                id, json, model, TimeUtil.text(LocalDateTime.now()));
    }
    private void metric(Long id, String code, double value) { jdbcTemplate.update("INSERT INTO quant_experiment_metric(experiment_id,metric_code,metric_value) VALUES(?,?,?)", id, code, value); }

    private void saveEquityPoints(final Long experimentId, final List<EquityPoint> values) {
        jdbcTemplate.batchUpdate("INSERT INTO quant_equity_point(experiment_id,trade_date,portfolio_nav,"
                + "benchmark_nav,cash,total_asset,drawdown) VALUES(?,?,?,?,?,?,?)", new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int index) throws SQLException {
                EquityPoint value = values.get(index); ps.setLong(1, experimentId); ps.setString(2, value.getTradeDate().toString());
                ps.setDouble(3, value.getPortfolioNav()); ps.setDouble(4, value.getBenchmarkNav()); ps.setDouble(5, value.getCash());
                ps.setDouble(6, value.getTotalAsset()); ps.setDouble(7, value.getDrawdown());
            }
            @Override public int getBatchSize() { return values.size(); }
        });
    }

    private void saveTrades(final Long experimentId, final List<BacktestTrade> values) {
        jdbcTemplate.batchUpdate("INSERT INTO quant_trade(experiment_id,signal_date,trade_date,instrument_code,"
                + "side,quantity,price,notional,fee,reason) VALUES(?,?,?,?,?,?,?,?,?,?)", new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int index) throws SQLException {
                BacktestTrade value = values.get(index); ps.setLong(1, experimentId); ps.setString(2, value.getSignalDate().toString());
                ps.setString(3, value.getTradeDate().toString()); ps.setString(4, value.getInstrumentCode()); ps.setString(5, value.getSide());
                ps.setLong(6, value.getQuantity()); ps.setDouble(7, value.getPrice()); ps.setDouble(8, value.getNotional());
                ps.setDouble(9, value.getFee()); ps.setString(10, value.getReason());
            }
            @Override public int getBatchSize() { return values.size(); }
        });
    }

    private void saveWarnings(final Long experimentId, final List<String> values) {
        jdbcTemplate.batchUpdate("INSERT INTO quant_experiment_warning(experiment_id,warning_index,message) VALUES(?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override public void setValues(PreparedStatement ps, int index) throws SQLException {
                        ps.setLong(1, experimentId); ps.setInt(2, index); ps.setString(3, values.get(index));
                    }
                    @Override public int getBatchSize() { return values == null ? 0 : values.size(); }
                });
    }
    private void saveAnnual(final Long experimentId, final List<AnnualPerformance> values) {
        jdbcTemplate.batchUpdate("INSERT INTO quant_experiment_year(experiment_id,year,portfolio_return,benchmark_return,excess_return,max_drawdown) VALUES(?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override public void setValues(PreparedStatement ps, int index) throws SQLException {
                        AnnualPerformance value = values.get(index); ps.setLong(1, experimentId); ps.setInt(2, value.getYear());
                        ps.setDouble(3, value.getPortfolioReturn()); ps.setDouble(4, value.getBenchmarkReturn());
                        ps.setDouble(5, value.getExcessReturn()); ps.setDouble(6, value.getMaxDrawdown());
                    }
                    @Override public int getBatchSize() { return values == null ? 0 : values.size(); }
                });
    }
    private void savePositions(final Long experimentId, final List<PositionSnapshot> values) {
        jdbcTemplate.batchUpdate("INSERT INTO quant_position_snapshot(experiment_id,trade_date,instrument_code,quantity,price,market_value,weight) VALUES(?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override public void setValues(PreparedStatement ps, int index) throws SQLException {
                        PositionSnapshot value = values.get(index); ps.setLong(1, experimentId); ps.setString(2, value.getTradeDate().toString());
                        ps.setString(3, value.getInstrumentCode()); ps.setLong(4, value.getQuantity()); ps.setDouble(5, value.getPrice());
                        ps.setDouble(6, value.getMarketValue()); ps.setDouble(7, value.getWeight());
                    }
                    @Override public int getBatchSize() { return values == null ? 0 : values.size(); }
                });
    }

    private BacktestResult loadResult(Long id) {
        BacktestResult result = new BacktestResult(); BacktestMetrics metrics = new BacktestMetrics();
        java.util.Map<String, Double> values = new java.util.HashMap<String, Double>();
        jdbcTemplate.query("SELECT metric_code,metric_value FROM quant_experiment_metric WHERE experiment_id=?",
                rs -> { values.put(rs.getString(1), rs.getDouble(2)); }, id);
        metrics.setTotalReturn(v(values,"TOTAL_RETURN")); metrics.setAnnualizedReturn(v(values,"ANNUAL_RETURN"));
        metrics.setAnnualizedVolatility(v(values,"VOLATILITY")); metrics.setMaxDrawdown(v(values,"MAX_DRAWDOWN"));
        metrics.setSharpeRatio(v(values,"SHARPE")); metrics.setCalmarRatio(v(values,"CALMAR")); metrics.setWinRate(v(values,"WIN_RATE"));
        metrics.setTurnover(v(values,"TURNOVER")); metrics.setTradeCount((int) v(values,"TRADE_COUNT"));
        metrics.setBenchmarkReturn(v(values,"BENCHMARK_RETURN")); metrics.setExcessReturn(v(values,"EXCESS_RETURN")); result.setMetrics(metrics);
        result.setEquityCurve(jdbcTemplate.query("SELECT * FROM quant_equity_point WHERE experiment_id=? ORDER BY trade_date", (rs,row)-> {
            EquityPoint p = new EquityPoint(); p.setTradeDate(LocalDate.parse(rs.getString("trade_date"))); p.setPortfolioNav(rs.getDouble("portfolio_nav"));
            p.setBenchmarkNav(rs.getDouble("benchmark_nav")); p.setCash(rs.getDouble("cash")); p.setTotalAsset(rs.getDouble("total_asset")); p.setDrawdown(rs.getDouble("drawdown")); return p;
        }, id));
        result.setTrades(jdbcTemplate.query("SELECT * FROM quant_trade WHERE experiment_id=? ORDER BY trade_date,id", (rs,row)-> {
            BacktestTrade t = new BacktestTrade(); t.setSignalDate(LocalDate.parse(rs.getString("signal_date"))); t.setTradeDate(LocalDate.parse(rs.getString("trade_date")));
            t.setInstrumentCode(rs.getString("instrument_code")); t.setSide(rs.getString("side")); t.setQuantity(rs.getLong("quantity"));
            t.setPrice(rs.getDouble("price")); t.setNotional(rs.getDouble("notional")); t.setFee(rs.getDouble("fee")); t.setReason(rs.getString("reason")); return t;
        }, id));
        result.setWarnings(jdbcTemplate.query("SELECT message FROM quant_experiment_warning WHERE experiment_id=? ORDER BY warning_index",
                (rs,row) -> rs.getString(1), id));
        result.setAnnualPerformance(jdbcTemplate.query("SELECT * FROM quant_experiment_year WHERE experiment_id=? ORDER BY year", (rs,row) -> {
            AnnualPerformance value = new AnnualPerformance(); value.setYear(rs.getInt("year"));
            value.setPortfolioReturn(rs.getDouble("portfolio_return")); value.setBenchmarkReturn(rs.getDouble("benchmark_return"));
            value.setExcessReturn(rs.getDouble("excess_return")); value.setMaxDrawdown(rs.getDouble("max_drawdown")); return value;
        }, id));
        result.setPositions(jdbcTemplate.query("SELECT * FROM quant_position_snapshot WHERE experiment_id=? ORDER BY trade_date,instrument_code", (rs,row) -> {
            PositionSnapshot value = new PositionSnapshot(); value.setTradeDate(LocalDate.parse(rs.getString("trade_date")));
            value.setInstrumentCode(rs.getString("instrument_code")); value.setQuantity(rs.getLong("quantity")); value.setPrice(rs.getDouble("price"));
            value.setMarketValue(rs.getDouble("market_value")); value.setWeight(rs.getDouble("weight")); return value;
        }, id));
        return result;
    }
    private double v(java.util.Map<String, Double> values, String key) { return values.containsKey(key) ? values.get(key) : 0d; }
}
