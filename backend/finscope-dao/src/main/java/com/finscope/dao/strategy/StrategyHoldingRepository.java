package com.finscope.dao.strategy;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.StrategyHolding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StrategyHoldingRepository {
    private static final String SELECT = "SELECT h.*, i.code, i.type, i.name FROM strategy_holding h "
            + "JOIN instrument i ON i.id=h.instrument_id ";

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<StrategyHolding> mapper = (rs, rowNum) -> {
        StrategyHolding holding = new StrategyHolding();
        holding.setId(rs.getLong("id"));
        holding.setInstrumentId(rs.getLong("instrument_id"));
        holding.setRole(rs.getString("role"));
        holding.setTargetWeight(rs.getDouble("target_weight"));
        holding.setCurrentWeight(rs.getDouble("current_weight"));
        holding.setNote(rs.getString("note"));
        holding.setSortOrder(rs.getInt("sort_order"));
        holding.setRevision(rs.getLong("revision"));
        holding.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        holding.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        holding.setCode(rs.getString("code"));
        holding.setType(rs.getString("type"));
        holding.setName(rs.getString("name"));
        return holding;
    };

    public StrategyHolding save(StrategyHolding holding) {
        LocalDateTime now = LocalDateTime.now();
        holding.setCreatedAt(now);
        holding.setUpdatedAt(now);
        holding.setRevision(0);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO strategy_holding("
                    + "instrument_id,role,target_weight,current_weight,note,sort_order,revision,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, holding.getInstrumentId());
            ps.setString(2, holding.getRole());
            ps.setDouble(3, holding.getTargetWeight());
            ps.setDouble(4, holding.getCurrentWeight());
            ps.setString(5, holding.getNote());
            ps.setInt(6, holding.getSortOrder());
            ps.setLong(7, holding.getRevision());
            ps.setString(8, TimeUtil.text(now));
            ps.setString(9, TimeUtil.text(now));
            return ps;
        }, keys);
        if (keys.getKey() != null) {
            holding.setId(keys.getKey().longValue());
        }
        return findById(holding.getId()).orElse(holding);
    }

    public List<StrategyHolding> findAllWithInstrument() {
        return jdbcTemplate.query(SELECT + "ORDER BY h.sort_order ASC, h.id ASC", mapper);
    }

    public Optional<StrategyHolding> findById(Long id) {
        List<StrategyHolding> results = jdbcTemplate.query(SELECT + "WHERE h.id=?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public double sumTargetWeightExcluding(Long id) {
        Double result = id == null
                ? jdbcTemplate.queryForObject("SELECT COALESCE(SUM(target_weight), 0) FROM strategy_holding", Double.class)
                : jdbcTemplate.queryForObject("SELECT COALESCE(SUM(target_weight), 0) FROM strategy_holding WHERE id<>?", Double.class, id);
        return result == null ? 0 : result;
    }

    public boolean update(Long id, String role, double targetWeight, double currentWeight, String note, long revision) {
        return jdbcTemplate.update("UPDATE strategy_holding SET role=?,target_weight=?,current_weight=?,note=?,"
                        + "revision=revision+1,updated_at=? WHERE id=? AND revision=?",
                role, targetWeight, currentWeight, note, TimeUtil.text(LocalDateTime.now()), id, revision) == 1;
    }

    public boolean deleteByIdAndRevision(Long id, long revision) {
        return jdbcTemplate.update("DELETE FROM strategy_holding WHERE id=? AND revision=?", id, revision) == 1;
    }
}
