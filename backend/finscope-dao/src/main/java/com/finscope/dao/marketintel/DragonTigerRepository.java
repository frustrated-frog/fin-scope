package com.finscope.dao.marketintel;

import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.DragonTigerSeat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class DragonTigerRepository {
    private final JdbcTemplate jdbc;

    public DragonTigerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void saveAll(List<DragonTigerRecord> records) {
        if (records == null) {
            return;
        }
        for (DragonTigerRecord record : records) {
            save(record);
        }
    }

    private void save(DragonTigerRecord record) {
        jdbc.update("INSERT OR IGNORE INTO market_dragon_tiger_record(" +
                        "instrument_id,provider_code,trade_date,external_id,reason_code,reason," +
                        "provider_explanation,close_price,change_rate,buy_amount,sell_amount,net_amount," +
                        "billboard_amount,market_amount,net_amount_ratio,billboard_amount_ratio,turnover_rate," +
                        "free_market_cap,retrieved_at,payload_hash,quality_status) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                record.getInstrumentId(), record.getProviderCode(), text(record.getTradeDate()),
                record.getExternalId(), record.getReasonCode(), record.getReason(),
                record.getProviderExplanation(), text(record.getClosePrice()), text(record.getChangeRate()),
                text(record.getBuyAmount()), text(record.getSellAmount()), text(record.getNetAmount()),
                text(record.getBillboardAmount()), text(record.getMarketAmount()),
                text(record.getNetAmountRatio()), text(record.getBillboardAmountRatio()),
                text(record.getTurnoverRate()), text(record.getFreeMarketCap()),
                text(record.getRetrievedAt()), record.getPayloadHash(), record.getQualityStatus());
        Long recordId = jdbc.queryForObject("SELECT id FROM market_dragon_tiger_record WHERE " +
                        "instrument_id=? AND provider_code=? AND trade_date=? AND external_id=? AND payload_hash=?",
                Long.class, record.getInstrumentId(), record.getProviderCode(), text(record.getTradeDate()),
                record.getExternalId(), record.getPayloadHash());
        record.setId(recordId);
        for (DragonTigerSeat seat : record.getSeats()) {
            seat.setRecordId(recordId);
            jdbc.update("INSERT OR IGNORE INTO market_dragon_tiger_seat(" +
                            "record_id,external_trade_id,seat_code,seat_name,direction,rank,buy_amount,sell_amount," +
                            "net_amount,buy_ratio,sell_ratio,seat_type,institutional,northbound,retrieved_at,payload_hash) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    recordId, seat.getExternalTradeId(), safe(seat.getSeatCode()), seat.getSeatName(),
                    seat.getDirection(), seat.getRank(), text(seat.getBuyAmount()), text(seat.getSellAmount()),
                    text(seat.getNetAmount()), text(seat.getBuyRatio()), text(seat.getSellRatio()),
                    seat.getSeatType(), seat.isInstitutional() ? 1 : 0, seat.isNorthbound() ? 1 : 0,
                    text(seat.getRetrievedAt()), seat.getPayloadHash());
            Long seatId = jdbc.queryForObject("SELECT id FROM market_dragon_tiger_seat WHERE " +
                            "record_id=? AND direction=? AND rank=? AND seat_code=? AND seat_name=?",
                    Long.class, recordId, seat.getDirection(), seat.getRank(),
                    safe(seat.getSeatCode()), seat.getSeatName());
            seat.setId(seatId);
        }
    }

    public List<DragonTigerRecord> findLatestBusinessVersions(
            Long instrumentId, LocalDate startDate, LocalDate endDate) {
        List<DragonTigerRecord> records = jdbc.query(
                "SELECT * FROM (" +
                        "SELECT r.*,ROW_NUMBER() OVER (" +
                        "PARTITION BY instrument_id,provider_code,trade_date,external_id " +
                        "ORDER BY retrieved_at DESC,id DESC) rn " +
                        "FROM market_dragon_tiger_record r " +
                        "WHERE instrument_id=? AND trade_date BETWEEN ? AND ?) latest " +
                        "WHERE rn=1 ORDER BY trade_date DESC,id DESC",
                recordMapper, instrumentId, text(startDate), text(endDate));
        for (DragonTigerRecord record : records) {
            record.setSeats(findSeats(record.getId()));
        }
        return Collections.unmodifiableList(new ArrayList<DragonTigerRecord>(records));
    }

    private List<DragonTigerSeat> findSeats(Long recordId) {
        return jdbc.query("SELECT * FROM market_dragon_tiger_seat WHERE record_id=? " +
                "ORDER BY CASE direction WHEN 'BUY' THEN 0 ELSE 1 END,rank ASC,id ASC",
                seatMapper, recordId);
    }

    private final RowMapper<DragonTigerRecord> recordMapper = (rs, row) -> {
        DragonTigerRecord record = new DragonTigerRecord();
        record.setId(rs.getLong("id"));
        record.setInstrumentId(rs.getLong("instrument_id"));
        record.setProviderCode(rs.getString("provider_code"));
        record.setTradeDate(LocalDate.parse(rs.getString("trade_date")));
        record.setExternalId(rs.getString("external_id"));
        record.setReasonCode(rs.getString("reason_code"));
        record.setReason(rs.getString("reason"));
        record.setProviderExplanation(rs.getString("provider_explanation"));
        record.setClosePrice(decimal(rs.getString("close_price")));
        record.setChangeRate(decimal(rs.getString("change_rate")));
        record.setBuyAmount(decimal(rs.getString("buy_amount")));
        record.setSellAmount(decimal(rs.getString("sell_amount")));
        record.setNetAmount(decimal(rs.getString("net_amount")));
        record.setBillboardAmount(decimal(rs.getString("billboard_amount")));
        record.setMarketAmount(decimal(rs.getString("market_amount")));
        record.setNetAmountRatio(decimal(rs.getString("net_amount_ratio")));
        record.setBillboardAmountRatio(decimal(rs.getString("billboard_amount_ratio")));
        record.setTurnoverRate(decimal(rs.getString("turnover_rate")));
        record.setFreeMarketCap(decimal(rs.getString("free_market_cap")));
        record.setRetrievedAt(LocalDateTime.parse(rs.getString("retrieved_at")));
        record.setPayloadHash(rs.getString("payload_hash"));
        record.setQualityStatus(rs.getString("quality_status"));
        return record;
    };

    private final RowMapper<DragonTigerSeat> seatMapper = (rs, row) -> {
        DragonTigerSeat seat = new DragonTigerSeat();
        seat.setId(rs.getLong("id"));
        seat.setRecordId(rs.getLong("record_id"));
        seat.setExternalTradeId(rs.getString("external_trade_id"));
        seat.setSeatCode(emptyToNull(rs.getString("seat_code")));
        seat.setSeatName(rs.getString("seat_name"));
        seat.setDirection(rs.getString("direction"));
        seat.setRank(rs.getInt("rank"));
        seat.setBuyAmount(decimal(rs.getString("buy_amount")));
        seat.setSellAmount(decimal(rs.getString("sell_amount")));
        seat.setNetAmount(decimal(rs.getString("net_amount")));
        seat.setBuyRatio(decimal(rs.getString("buy_ratio")));
        seat.setSellRatio(decimal(rs.getString("sell_ratio")));
        seat.setSeatType(rs.getString("seat_type"));
        seat.setInstitutional(rs.getInt("institutional") == 1);
        seat.setNorthbound(rs.getInt("northbound") == 1);
        seat.setRetrievedAt(LocalDateTime.parse(rs.getString("retrieved_at")));
        seat.setPayloadHash(rs.getString("payload_hash"));
        return seat;
    };

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
