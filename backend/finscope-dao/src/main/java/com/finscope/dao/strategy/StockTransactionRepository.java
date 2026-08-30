package com.finscope.dao.strategy;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StockTransactionRepository {
    private static final String SELECT = "SELECT t.*,i.code AS instrument_code,i.name AS instrument_name,i.market AS instrument_market "
            + "FROM stock_transaction t LEFT JOIN instrument i ON i.id=t.instrument_id ";

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<StockTransaction> mapper = (rs, rowNum) -> {
        StockTransaction value = new StockTransaction();
        value.setId(rs.getLong("id"));
        value.setClientRequestId(rs.getString("client_request_id"));
        long instrumentId = rs.getLong("instrument_id");
        value.setInstrumentId(rs.wasNull() ? null : instrumentId);
        String code = rs.getString("instrument_code");
        String market = rs.getString("instrument_market");
        value.setInstrumentCode(code == null ? null : code + "." + (market == null ? market(code) : market));
        value.setInstrumentName(rs.getString("instrument_name"));
        value.setType(StockTransactionType.valueOf(rs.getString("event_type")));
        value.setTradeDate(java.time.LocalDate.parse(rs.getString("trade_date")));
        value.setQuantity(rs.getBigDecimal("quantity"));
        value.setPrice(rs.getBigDecimal("price"));
        value.setCommission(rs.getBigDecimal("commission"));
        value.setStampDuty(rs.getBigDecimal("stamp_duty"));
        value.setTransferFee(rs.getBigDecimal("transfer_fee"));
        value.setOtherFee(rs.getBigDecimal("other_fee"));
        value.setCashAmount(rs.getBigDecimal("cash_amount"));
        long reversalOfId = rs.getLong("reversal_of_id");
        value.setReversalOfId(rs.wasNull() ? null : reversalOfId);
        value.setNote(rs.getString("note"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public StockTransaction save(StockTransaction value) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO stock_transaction(client_request_id,instrument_id,event_type,trade_date,quantity,price,"
                            + "commission,stamp_duty,transfer_fee,other_fee,cash_amount,reversal_of_id,note,created_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, value.getClientRequestId());
            setLong(statement, 2, value.getInstrumentId());
            statement.setString(3, value.getType().name());
            statement.setString(4, value.getTradeDate().toString());
            statement.setBigDecimal(5, value.getQuantity());
            statement.setBigDecimal(6, value.getPrice());
            statement.setBigDecimal(7, value.getCommission());
            statement.setBigDecimal(8, value.getStampDuty());
            statement.setBigDecimal(9, value.getTransferFee());
            statement.setBigDecimal(10, value.getOtherFee());
            statement.setBigDecimal(11, value.getCashAmount());
            setLong(statement, 12, value.getReversalOfId());
            statement.setString(13, value.getNote());
            statement.setString(14, TimeUtil.text(now));
            return statement;
        }, keys);
        value.setId(keys.getKey().longValue());
        value.setCreatedAt(now);
        return findById(value.getId()).orElse(value);
    }

    public Optional<StockTransaction> findById(Long id) {
        List<StockTransaction> values = jdbcTemplate.query(SELECT + "WHERE t.id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<StockTransaction> findByClientRequestId(String clientRequestId) {
        List<StockTransaction> values = jdbcTemplate.query(
                SELECT + "WHERE t.client_request_id=?", mapper, clientRequestId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<StockTransaction> findAll(int limit) {
        int bounded = Math.max(1, Math.min(limit, 1000));
        return jdbcTemplate.query(SELECT + "ORDER BY t.trade_date ASC,t.id ASC LIMIT ?", mapper, bounded);
    }

    public List<StockTransaction> findAllDescending(int limit) {
        int bounded = Math.max(1, Math.min(limit, 1000));
        return jdbcTemplate.query(SELECT + "ORDER BY t.trade_date DESC,t.id DESC LIMIT ?", mapper, bounded);
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    private static String market(String code) {
        if (code.startsWith("6")) {
            return "SH";
        }
        if (code.startsWith("8") || code.startsWith("4")) {
            return "BJ";
        }
        return "SZ";
    }
}
