package com.finscope.dao.quant;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class QuantMarketDataRepository {
    @Resource private JdbcTemplate jdbcTemplate;

    private final RowMapper<QuantDailyBar> barMapper = (rs, rowNum) -> {
        QuantDailyBar value = new QuantDailyBar();
        value.setId(rs.getLong("id")); value.setDatasetId(rs.getLong("dataset_id"));
        value.setTradeDate(LocalDate.parse(rs.getString("trade_date")));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setOpen(rs.getBigDecimal("open")); value.setHigh(rs.getBigDecimal("high"));
        value.setLow(rs.getBigDecimal("low")); value.setClose(rs.getBigDecimal("close"));
        value.setAdjustedClose(rs.getBigDecimal("adjusted_close"));
        value.setVolume(rs.getBigDecimal("volume")); value.setAmount(rs.getBigDecimal("amount"));
        value.setTradeStatus(rs.getString("trade_status")); value.setSt(rs.getInt("is_st") == 1);
        value.setLimitUp(rs.getInt("limit_up") == 1); value.setLimitDown(rs.getInt("limit_down") == 1);
        return value;
    };

    private final RowMapper<QuantFundamentalSnapshot> fundamentalMapper = (rs, rowNum) -> {
        QuantFundamentalSnapshot value = new QuantFundamentalSnapshot();
        value.setId(rs.getLong("id")); value.setDatasetId(rs.getLong("dataset_id"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setReportPeriod(LocalDate.parse(rs.getString("report_period")));
        value.setDisclosedAt(LocalDate.parse(rs.getString("disclosed_at")));
        value.setPe(rs.getBigDecimal("pe")); value.setPb(rs.getBigDecimal("pb"));
        value.setMarketCap(rs.getBigDecimal("market_cap")); value.setRoe(rs.getBigDecimal("roe"));
        value.setRevenueGrowth(rs.getBigDecimal("revenue_growth"));
        value.setProfitGrowth(rs.getBigDecimal("profit_growth")); value.setDebtRatio(rs.getBigDecimal("debt_ratio"));
        return value;
    };

    public void insertBars(List<QuantDailyBar> values) {
        final String sql = "INSERT INTO quant_daily_bar(dataset_id,trade_date,instrument_code,open,high,low,close,"
                + "adjusted_close,volume,amount,trade_status,is_st,limit_up,limit_down) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                QuantDailyBar value = values.get(i);
                ps.setLong(1, value.getDatasetId()); ps.setString(2, value.getTradeDate().toString());
                ps.setString(3, value.getInstrumentCode()); ps.setBigDecimal(4, value.getOpen());
                ps.setBigDecimal(5, value.getHigh()); ps.setBigDecimal(6, value.getLow());
                ps.setBigDecimal(7, value.getClose()); ps.setBigDecimal(8, value.getAdjustedClose());
                ps.setBigDecimal(9, value.getVolume()); ps.setBigDecimal(10, value.getAmount());
                ps.setString(11, value.getTradeStatus()); ps.setInt(12, value.isSt() ? 1 : 0);
                ps.setInt(13, value.isLimitUp() ? 1 : 0); ps.setInt(14, value.isLimitDown() ? 1 : 0);
            }
            @Override public int getBatchSize() { return values.size(); }
        });
    }

    public void insertFundamentals(List<QuantFundamentalSnapshot> values) {
        final String sql = "INSERT INTO quant_fundamental_snapshot(dataset_id,instrument_code,report_period,"
                + "disclosed_at,pe,pb,market_cap,roe,revenue_growth,profit_growth,debt_ratio) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                QuantFundamentalSnapshot value = values.get(i);
                ps.setLong(1, value.getDatasetId()); ps.setString(2, value.getInstrumentCode());
                ps.setString(3, value.getReportPeriod().toString()); ps.setString(4, value.getDisclosedAt().toString());
                ps.setBigDecimal(5, value.getPe()); ps.setBigDecimal(6, value.getPb());
                ps.setBigDecimal(7, value.getMarketCap()); ps.setBigDecimal(8, value.getRoe());
                ps.setBigDecimal(9, value.getRevenueGrowth()); ps.setBigDecimal(10, value.getProfitGrowth());
                ps.setBigDecimal(11, value.getDebtRatio());
            }
            @Override public int getBatchSize() { return values.size(); }
        });
    }

    public List<QuantDailyBar> findBars(Long datasetId) {
        return jdbcTemplate.query("SELECT * FROM quant_daily_bar WHERE dataset_id=? ORDER BY trade_date,instrument_code",
                barMapper, datasetId);
    }

    public Optional<QuantFundamentalSnapshot> latestVisibleFundamental(Long datasetId, String code, LocalDate signalDate) {
        List<QuantFundamentalSnapshot> values = jdbcTemplate.query("SELECT * FROM quant_fundamental_snapshot "
                        + "WHERE dataset_id=? AND instrument_code=? AND disclosed_at<=? "
                        + "ORDER BY disclosed_at DESC,report_period DESC LIMIT 1", fundamentalMapper,
                datasetId, code, signalDate.toString());
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<QuantFundamentalSnapshot> findFundamentals(Long datasetId) {
        return jdbcTemplate.query("SELECT * FROM quant_fundamental_snapshot WHERE dataset_id=? "
                + "ORDER BY disclosed_at,instrument_code,report_period", fundamentalMapper, datasetId);
    }
}
