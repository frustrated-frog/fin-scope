package com.finscope.dao.instrument;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.instrument.WatchlistItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class WatchlistRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private static final String SELECT_WITH_INSTRUMENT =
            "SELECT w.id, w.instrument_id, w.group_name, w.sort_order, w.created_at, "
                    + "i.code, i.type, i.name, i.market, i.sector_code "
                    + "FROM watchlist_item w JOIN instrument i ON w.instrument_id = i.id ";

    private final RowMapper<WatchlistItem> mapper = (rs, rowNum) -> {
        WatchlistItem item = new WatchlistItem();
        item.setId(rs.getLong("id"));
        item.setInstrumentId(rs.getLong("instrument_id"));
        item.setGroupName(rs.getString("group_name"));
        item.setSortOrder(rs.getInt("sort_order"));
        item.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        item.setCode(rs.getString("code"));
        item.setType(rs.getString("type"));
        item.setName(rs.getString("name"));
        item.setMarket(rs.getString("market"));
        item.setSectorCode(rs.getString("sector_code"));
        return item;
    };

    public WatchlistItem save(WatchlistItem item) {
        item.setCreatedAt(LocalDateTime.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO watchlist_item(instrument_id,group_name,sort_order,created_at) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, item.getInstrumentId());
            ps.setString(2, item.getGroupName());
            ps.setInt(3, item.getSortOrder());
            ps.setString(4, TimeUtil.text(item.getCreatedAt()));
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            item.setId(keyHolder.getKey().longValue());
        }
        return item;
    }

    public List<WatchlistItem> findAll() {
        return jdbcTemplate.query(SELECT_WITH_INSTRUMENT + "ORDER BY w.sort_order ASC, w.id DESC", mapper);
    }

    public List<WatchlistItem> findByTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(types.size(), "?"));
        return jdbcTemplate.query(SELECT_WITH_INSTRUMENT + "WHERE i.type IN (" + placeholders + ") "
                + "ORDER BY w.sort_order ASC, w.id DESC", mapper, types.toArray());
    }

    public Optional<WatchlistItem> findById(Long id) {
        List<WatchlistItem> items = jdbcTemplate.query(
                SELECT_WITH_INSTRUMENT + "WHERE w.id = ?", mapper, id);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    public Optional<WatchlistItem> findByCodeAndType(String code, String type) {
        List<WatchlistItem> items = jdbcTemplate.query(
                SELECT_WITH_INSTRUMENT + "WHERE i.code = ? AND i.type = ?", mapper, code, type);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    public boolean existsByInstrumentId(Long instrumentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_item WHERE instrument_id = ?", Integer.class, instrumentId);
        return count != null && count > 0;
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM watchlist_item WHERE id = ?", id);
    }

    public int deleteByCodeAndType(String code, String type) {
        return jdbcTemplate.update("DELETE FROM watchlist_item WHERE instrument_id IN "
                + "(SELECT id FROM instrument WHERE code = ? AND type = ?)", code, type);
    }

    /** 更新分组名（null/空表示移出分组，归入默认组）。 */
    public int updateGroup(Long id, String groupName) {
        return jdbcTemplate.update("UPDATE watchlist_item SET group_name = ? WHERE id = ?", groupName, id);
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_item WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
}
