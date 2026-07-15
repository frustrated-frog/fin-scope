package com.finscope.dao.factorresearch;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class QuantCapitalFlowRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<QuantCapitalFlowDaily> mapper = (rs, rowNum) -> {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily();
        value.setDatasetId(rs.getLong("dataset_id"));
        value.setTradeDate(TimeUtil.localDate(rs, "trade_date"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setAvailableAt(TimeUtil.localDateTime(rs, "available_at"));
        value.setSourceFlowId(rs.getLong("source_flow_id"));
        value.setProviderCode(rs.getString("provider_code"));
        value.setMainNetInflow(decimal(rs.getString("main_net_inflow")));
        value.setMainFlowShare(decimal(rs.getString("main_flow_share")));
        value.setSuperLargeNetInflow(decimal(rs.getString("super_large_net_inflow")));
        value.setLargeNetInflow(decimal(rs.getString("large_net_inflow")));
        value.setMediumNetInflow(decimal(rs.getString("medium_net_inflow")));
        value.setSmallNetInflow(decimal(rs.getString("small_net_inflow")));
        value.setTurnoverRate(decimal(rs.getString("turnover_rate")));
        value.setAmount(decimal(rs.getString("amount")));
        value.setQualityStatus(rs.getString("quality_status"));
        value.setSourceFingerprint(rs.getString("source_fingerprint"));
        value.setCalculationVersion(rs.getString("calculation_version"));
        return value;
    };

    public void saveAll(final List<QuantCapitalFlowDaily> values) {
        final String sql = "INSERT INTO quant_capital_flow_daily("
                + "dataset_id,trade_date,instrument_code,available_at,source_flow_id,provider_code,"
                + "main_net_inflow,main_flow_share,super_large_net_inflow,large_net_inflow,"
                + "medium_net_inflow,small_net_inflow,turnover_rate,amount,quality_status,"
                + "source_fingerprint,calculation_version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                QuantCapitalFlowDaily value = values.get(i);
                ps.setLong(1, value.getDatasetId());
                ps.setString(2, TimeUtil.text(value.getTradeDate()));
                ps.setString(3, value.getInstrumentCode());
                ps.setString(4, TimeUtil.text(value.getAvailableAt()));
                ps.setLong(5, value.getSourceFlowId());
                ps.setString(6, value.getProviderCode());
                ps.setString(7, text(value.getMainNetInflow()));
                ps.setString(8, text(value.getMainFlowShare()));
                ps.setString(9, text(value.getSuperLargeNetInflow()));
                ps.setString(10, text(value.getLargeNetInflow()));
                ps.setString(11, text(value.getMediumNetInflow()));
                ps.setString(12, text(value.getSmallNetInflow()));
                ps.setString(13, text(value.getTurnoverRate()));
                ps.setString(14, text(value.getAmount()));
                ps.setString(15, value.getQualityStatus());
                ps.setString(16, value.getSourceFingerprint());
                ps.setString(17, value.getCalculationVersion());
            }

            @Override
            public int getBatchSize() {
                return values.size();
            }
        });
    }

    public List<QuantCapitalFlowDaily> findByDatasetId(Long datasetId) {
        return jdbcTemplate.query("SELECT * FROM quant_capital_flow_daily WHERE dataset_id=? "
                        + "ORDER BY trade_date,instrument_code",
                mapper, datasetId);
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
