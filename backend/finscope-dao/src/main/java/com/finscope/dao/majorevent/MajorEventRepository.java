package com.finscope.dao.majorevent;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.majorevent.MajorEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MajorEventRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<MajorEvent> mapper = (rs, rowNum) -> {
        MajorEvent value = new MajorEvent();
        value.setId(rs.getLong("id"));
        value.setOriginType(rs.getString("origin_type"));
        value.setOriginKey(rs.getString("origin_key"));
        value.setTitle(rs.getString("title"));
        value.setSummary(rs.getString("summary"));
        value.setSourceName(rs.getString("source_name"));
        value.setSourceUrl(rs.getString("source_url"));
        value.setCategoryCode(rs.getString("category_code"));
        value.setOccurredDate(TimeUtil.localDate(rs, "occurred_date"));
        value.setNote(rs.getString("note"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    public MajorEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MajorEvent save(MajorEvent event) {
        LocalDateTime now = LocalDateTime.now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO major_event(origin_type,origin_key,title,summary,source_name,source_url,category_code,occurred_date,note,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, event.getOriginType());
            statement.setString(2, event.getOriginKey());
            statement.setString(3, event.getTitle());
            statement.setString(4, event.getSummary());
            statement.setString(5, event.getSourceName());
            statement.setString(6, event.getSourceUrl());
            statement.setString(7, event.getCategoryCode());
            statement.setString(8, TimeUtil.text(event.getOccurredDate()));
            statement.setString(9, event.getNote());
            statement.setString(10, TimeUtil.text(event.getCreatedAt()));
            statement.setString(11, TimeUtil.text(event.getUpdatedAt()));
            return statement;
        }, keyHolder);
        event.setId(keyHolder.getKey().longValue());
        return event;
    }

    public Optional<MajorEvent> findByOrigin(String originType, String originKey) {
        List<MajorEvent> values = jdbc.query("SELECT * FROM major_event WHERE origin_type=? AND origin_key=?", mapper, originType, originKey);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<MajorEvent> find(String originType, String categoryCode, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT * FROM major_event WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (hasText(originType)) { sql.append(" AND origin_type=?"); args.add(originType.trim()); }
        if (hasText(categoryCode)) { sql.append(" AND category_code=?"); args.add(categoryCode.trim()); }
        if (from != null) { sql.append(" AND occurred_date>=?"); args.add(TimeUtil.text(from)); }
        if (to != null) { sql.append(" AND occurred_date<=?"); args.add(TimeUtil.text(to)); }
        sql.append(" ORDER BY occurred_date DESC, created_at DESC, id DESC");
        return jdbc.query(sql.toString(), mapper, args.toArray());
    }

    public Optional<MajorEvent> findById(Long id) {
        List<MajorEvent> values = jdbc.query("SELECT * FROM major_event WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public MajorEvent update(MajorEvent event) {
        event.setUpdatedAt(LocalDateTime.now());
        jdbc.update("UPDATE major_event SET occurred_date=?,note=?,updated_at=? WHERE id=?",
                TimeUtil.text(event.getOccurredDate()), event.getNote(), TimeUtil.text(event.getUpdatedAt()), event.getId());
        return event;
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM major_event WHERE id=?", id);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
