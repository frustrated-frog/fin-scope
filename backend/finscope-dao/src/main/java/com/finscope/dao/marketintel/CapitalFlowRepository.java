package com.finscope.dao.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CapitalFlowRepository {
    private static final String INSERT = "INSERT OR IGNORE INTO market_capital_flow_snapshot(" +
            "instrument_id,provider_code,granularity,data_date,observed_at,price,trade_volume," +
            "interval_trade_amount,cumulative_trade_amount,turnover_rate,volume_ratio,main_inflow,main_outflow," +
            "main_net_inflow,super_large_net_inflow,large_net_inflow,medium_net_inflow,small_net_inflow," +
            "calculation_version,retrieved_at,payload_hash,quality_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private final JdbcTemplate jdbc;

    public CapitalFlowRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int saveAll(List<CapitalFlowPoint> points) {
        int inserted = 0;
        for (CapitalFlowPoint p : points) {
            inserted += jdbc.update(INSERT, p.getInstrumentId(), p.getProviderCode(), p.getGranularity(),
                    text(p.getDataDate()), text(p.getObservedAt()), text(p.getPrice()), text(p.getTradeVolume()),
                    text(p.getIntervalTradeAmount()), text(p.getCumulativeTradeAmount()), text(p.getTurnoverRate()),
                    text(p.getVolumeRatio()), text(p.getMainInflow()), text(p.getMainOutflow()),
                    text(p.getMainNetInflow()), text(p.getSuperLargeNetInflow()), text(p.getLargeNetInflow()),
                    text(p.getMediumNetInflow()), text(p.getSmallNetInflow()), p.getCalculationVersion(),
                    text(p.getRetrievedAt()), p.getPayloadHash(), p.getQualityStatus());
            Long id = jdbc.queryForObject("SELECT id FROM market_capital_flow_snapshot WHERE " +
                            "instrument_id=? AND provider_code=? AND granularity=? AND observed_at=? AND payload_hash=? " +
                            "AND calculation_version=?",
                    Long.class, p.getInstrumentId(), p.getProviderCode(), p.getGranularity(),
                    text(p.getObservedAt()), p.getPayloadHash(), p.getCalculationVersion());
            p.setId(id);
        }
        return inserted;
    }

    public List<CapitalFlowPoint> findRange(Long instrumentId, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT * FROM market_capital_flow_snapshot WHERE instrument_id=? " +
                        "AND observed_at>=? AND observed_at<=? ORDER BY observed_at ASC,id ASC",
                mapper, instrumentId, text(from), text(to));
    }

    public List<CapitalFlowPoint> findLatest(Long instrumentId, int limit) {
        return jdbc.query("SELECT * FROM market_capital_flow_snapshot WHERE instrument_id=? " +
                        "ORDER BY observed_at DESC,id DESC LIMIT ?", mapper, instrumentId, limit);
    }

    public List<CapitalFlowPoint> findLatestByGranularity(Long instrumentId, String granularity, int limit) {
        return jdbc.query("SELECT * FROM market_capital_flow_snapshot WHERE instrument_id=? AND granularity=? " +
                        "ORDER BY observed_at DESC,id DESC LIMIT ?", mapper, instrumentId, granularity, limit);
    }

    public List<CapitalFlowPoint> findDailyPointInTime(LocalDate from, LocalDate to, LocalDateTime asOfTime) {
        if (from == null || to == null || asOfTime == null) {
            throw new IllegalArgumentException("from, to and asOfTime are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return jdbc.query("SELECT * FROM (" +
                        "SELECT f.*,ROW_NUMBER() OVER (PARTITION BY instrument_id,data_date " +
                        "ORDER BY retrieved_at DESC,id DESC) AS rn " +
                        "FROM market_capital_flow_snapshot f WHERE granularity='DAY_1' " +
                        "AND data_date BETWEEN ? AND ? AND retrieved_at<=?) ranked " +
                        "WHERE rn=1 ORDER BY data_date ASC,instrument_id ASC",
                mapper, text(from), text(to), text(asOfTime));
    }

    private final RowMapper<CapitalFlowPoint> mapper = (rs, row) -> {
        CapitalFlowPoint p = new CapitalFlowPoint();
        p.setId(rs.getLong("id")); p.setInstrumentId(rs.getLong("instrument_id"));
        p.setProviderCode(rs.getString("provider_code")); p.setGranularity(rs.getString("granularity"));
        p.setDataDate(LocalDate.parse(rs.getString("data_date")));
        p.setObservedAt(LocalDateTime.parse(rs.getString("observed_at")));
        p.setPrice(decimal(rs.getString("price"))); p.setTradeVolume(decimal(rs.getString("trade_volume")));
        p.setIntervalTradeAmount(decimal(rs.getString("interval_trade_amount")));
        p.setCumulativeTradeAmount(decimal(rs.getString("cumulative_trade_amount")));
        p.setTurnoverRate(decimal(rs.getString("turnover_rate"))); p.setVolumeRatio(decimal(rs.getString("volume_ratio")));
        p.setMainInflow(decimal(rs.getString("main_inflow"))); p.setMainOutflow(decimal(rs.getString("main_outflow")));
        p.setMainNetInflow(decimal(rs.getString("main_net_inflow")));
        p.setSuperLargeNetInflow(decimal(rs.getString("super_large_net_inflow")));
        p.setLargeNetInflow(decimal(rs.getString("large_net_inflow")));
        p.setMediumNetInflow(decimal(rs.getString("medium_net_inflow")));
        p.setSmallNetInflow(decimal(rs.getString("small_net_inflow")));
        p.setCalculationVersion(rs.getString("calculation_version"));
        p.setRetrievedAt(LocalDateTime.parse(rs.getString("retrieved_at")));
        p.setPayloadHash(rs.getString("payload_hash")); p.setQualityStatus(rs.getString("quality_status"));
        return p;
    };
    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    private static String text(Object value) { return value == null ? null : value.toString(); }
}
