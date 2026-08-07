package com.finscope.dao.instrument;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.instrument.Instrument;
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
public class InstrumentRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Instrument> mapper = (rs, rowNum) -> {
        Instrument instrument = new Instrument();
        instrument.setId(rs.getLong("id"));
        instrument.setCode(rs.getString("code"));
        instrument.setType(rs.getString("type"));
        instrument.setName(rs.getString("name"));
        instrument.setMarket(rs.getString("market"));
        instrument.setAliases(rs.getString("aliases"));
        instrument.setSectorCode(rs.getString("sector_code"));
        instrument.setChainTags(rs.getString("chain_tags"));
        instrument.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        instrument.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return instrument;
    };

    public Instrument save(Instrument instrument) {
        LocalDateTime now = LocalDateTime.now();
        instrument.setCreatedAt(now);
        instrument.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO instrument(code,type,name,market,aliases,sector_code,chain_tags,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instrument.getCode());
            ps.setString(2, instrument.getType());
            ps.setString(3, instrument.getName());
            ps.setString(4, instrument.getMarket());
            ps.setString(5, instrument.getAliases());
            ps.setString(6, instrument.getSectorCode());
            ps.setString(7, instrument.getChainTags());
            ps.setString(8, TimeUtil.text(instrument.getCreatedAt()));
            ps.setString(9, TimeUtil.text(instrument.getUpdatedAt()));
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            instrument.setId(keyHolder.getKey().longValue());
        }
        return instrument;
    }

    public Optional<Instrument> findById(Long id) {
        List<Instrument> list = jdbcTemplate.query("SELECT * FROM instrument WHERE id = ?", mapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Instrument> findByCodeAndType(String code, String type) {
        List<Instrument> list = jdbcTemplate.query(
                "SELECT * FROM instrument WHERE code = ? AND type = ?", mapper, code, type);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Instrument> findByCodeTypeAndMarket(String code, String type, String market) {
        List<Instrument> list = jdbcTemplate.query(
                "SELECT * FROM instrument WHERE code = ? AND type = ? AND market = ?",
                mapper, code, type, market);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<Instrument> findAll() {
        return jdbcTemplate.query("SELECT * FROM instrument ORDER BY id DESC", mapper);
    }

    public Instrument update(Instrument instrument) {
        instrument.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE instrument SET name=?, market=?, aliases=?, sector_code=?, chain_tags=?, "
                        + "updated_at=? WHERE id=?",
                instrument.getName(), instrument.getMarket(), instrument.getAliases(),
                instrument.getSectorCode(), instrument.getChainTags(),
                TimeUtil.text(instrument.getUpdatedAt()), instrument.getId());
        return findById(instrument.getId()).orElse(instrument);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM instrument WHERE id = ?", id);
    }
}
