package com.finscope.dao.source;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.source.Source;
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
public class SourceRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Source> mapper = (rs, rowNum) -> {
        Source source = new Source();
        source.setId(rs.getLong("id"));
        source.setName(rs.getString("name"));
        source.setType(rs.getString("type"));
        source.setUrl(rs.getString("url"));
        source.setEnabled(rs.getInt("enabled") == 1);
        source.setFetchFrequencyMinutes(rs.getInt("fetch_frequency_minutes"));
        source.setCredibility(rs.getInt("credibility"));
        source.setTags(rs.getString("tags"));
        source.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        source.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return source;
    };

    public Source save(Source source) {
        LocalDateTime now = LocalDateTime.now();
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO source(name,type,url,enabled,fetch_frequency_minutes,credibility,tags,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, source.getName());
            ps.setString(2, source.getType());
            ps.setString(3, source.getUrl());
            ps.setInt(4, source.isEnabled() ? 1 : 0);
            ps.setInt(5, source.getFetchFrequencyMinutes());
            ps.setInt(6, source.getCredibility());
            ps.setString(7, source.getTags());
            ps.setString(8, TimeUtil.text(source.getCreatedAt()));
            ps.setString(9, TimeUtil.text(source.getUpdatedAt()));
            return ps;
        }, keyHolder);
        source.setId(keyHolder.getKey().longValue());
        return source;
    }

    public List<Source> findAll() {
        return jdbcTemplate.query("SELECT * FROM source ORDER BY id DESC", mapper);
    }

    public Optional<Source> findById(Long id) {
        List<Source> sources = jdbcTemplate.query("SELECT * FROM source WHERE id = ?", mapper, id);
        return sources.isEmpty() ? Optional.empty() : Optional.of(sources.get(0));
    }

    public Source update(Long id, Source source) {
        source.setId(id);
        source.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE source SET name=?, type=?, url=?, enabled=?, fetch_frequency_minutes=?, "
                        + "credibility=?, tags=?, updated_at=? WHERE id=?",
                source.getName(), source.getType(), source.getUrl(), source.isEnabled() ? 1 : 0,
                source.getFetchFrequencyMinutes(), source.getCredibility(), source.getTags(),
                TimeUtil.text(source.getUpdatedAt()), id);
        return findById(id).orElse(source);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM source WHERE id = ?", id);
    }
}
