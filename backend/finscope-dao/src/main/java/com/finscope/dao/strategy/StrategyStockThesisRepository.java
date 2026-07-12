package com.finscope.dao.strategy;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.StrategyStockThesis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StrategyStockThesisRepository {
    private static final String SELECT = "SELECT t.*,i.code,i.name FROM strategy_stock_thesis t "
            + "JOIN instrument i ON i.id=t.instrument_id ";

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<StrategyStockThesis> mapper = (rs, rowNum) -> {
        StrategyStockThesis value = new StrategyStockThesis();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        value.setStage(rs.getString("stage"));
        value.setThesis(rs.getString("thesis"));
        value.setBuyConditions(rs.getString("buy_conditions"));
        value.setInvalidationConditions(rs.getString("invalidation_conditions"));
        value.setWatchFocus(rs.getString("watch_focus"));
        value.setNote(rs.getString("note"));
        value.setRevision(rs.getLong("revision"));
        value.setCode(rs.getString("code"));
        value.setName(rs.getString("name"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    public StrategyStockThesis save(StrategyStockThesis value) {
        LocalDateTime now = LocalDateTime.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO strategy_stock_thesis(instrument_id,stage,thesis,buy_conditions,"
                            + "invalidation_conditions,watch_focus,note,revision,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, value.getInstrumentId());
            statement.setString(2, value.getStage());
            statement.setString(3, value.getThesis());
            statement.setString(4, value.getBuyConditions());
            statement.setString(5, value.getInvalidationConditions());
            statement.setString(6, value.getWatchFocus());
            statement.setString(7, value.getNote());
            statement.setLong(8, 0);
            statement.setString(9, TimeUtil.text(now));
            statement.setString(10, TimeUtil.text(now));
            return statement;
        }, keys);
        if (keys.getKey() != null) {
            value.setId(keys.getKey().longValue());
        }
        return findById(value.getId()).orElse(value);
    }

    public Optional<StrategyStockThesis> findById(Long id) {
        List<StrategyStockThesis> values = jdbcTemplate.query(SELECT + "WHERE t.id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public boolean existsByInstrumentId(Long instrumentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM strategy_stock_thesis WHERE instrument_id=?",
                Integer.class, instrumentId);
        return count != null && count > 0;
    }

    public List<StrategyStockThesis> findAllWithInstrument() {
        return jdbcTemplate.query(SELECT + "ORDER BY t.id DESC", mapper);
    }

    public boolean updateStage(Long id, String stage, long revision) {
        return jdbcTemplate.update(
                "UPDATE strategy_stock_thesis SET stage=?,revision=revision+1,updated_at=? "
                        + "WHERE id=? AND revision=?",
                stage, TimeUtil.text(LocalDateTime.now()), id, revision) == 1;
    }

    public boolean update(Long id, String stage, String thesis, String buyConditions,
                          String invalidationConditions, String watchFocus, String note,
                          long revision) {
        return jdbcTemplate.update(
                "UPDATE strategy_stock_thesis SET stage=?,thesis=?,buy_conditions=?,"
                        + "invalidation_conditions=?,watch_focus=?,note=?,revision=revision+1,"
                        + "updated_at=? WHERE id=? AND revision=?",
                stage, thesis, buyConditions, invalidationConditions, watchFocus, note,
                TimeUtil.text(LocalDateTime.now()), id, revision) == 1;
    }

    public boolean delete(Long id, long revision) {
        return jdbcTemplate.update(
                "DELETE FROM strategy_stock_thesis WHERE id=? AND revision=?", id, revision) == 1;
    }
}
