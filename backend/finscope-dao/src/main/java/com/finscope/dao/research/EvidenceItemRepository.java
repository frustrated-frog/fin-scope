package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.EvidenceItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Repository
public class EvidenceItemRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EvidenceItem> mapper = (rs, rowNum) -> {
        EvidenceItem item = new EvidenceItem();
        item.setId(rs.getLong("id"));
        item.setEventId(rs.getLong("event_id"));
        item.setArticleId(rs.getLong("article_id"));
        item.setSourceTier(rs.getString("source_tier"));
        item.setEvidenceType(rs.getString("evidence_type"));
        item.setClaim(rs.getString("claim"));
        item.setConfidence(rs.getInt("confidence"));
        item.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return item;
    };

    public EvidenceItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EvidenceItem save(EvidenceItem item) {
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO evidence_item(event_id,article_id,source_tier,evidence_type,claim,confidence,created_at) "
                            + "VALUES(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, item.getEventId());
            ps.setLong(2, item.getArticleId());
            ps.setString(3, item.getSourceTier());
            ps.setString(4, item.getEvidenceType());
            ps.setString(5, item.getClaim());
            ps.setInt(6, value(item.getConfidence()));
            ps.setString(7, TimeUtil.text(item.getCreatedAt()));
            return ps;
        }, keyHolder);
        item.setId(keyHolder.getKey().longValue());
        return item;
    }

    public List<EvidenceItem> findByEventId(Long eventId) {
        return jdbcTemplate.query("SELECT * FROM evidence_item WHERE event_id = ? ORDER BY created_at ASC, id ASC",
                mapper, eventId);
    }

    public java.util.Optional<EvidenceItem> findById(Long id) {
        List<EvidenceItem> items = jdbcTemplate.query("SELECT * FROM evidence_item WHERE id = ?", mapper, id);
        return items.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(items.get(0));
    }

    public List<EvidenceItem> findAll() {
        return jdbcTemplate.query("SELECT * FROM evidence_item ORDER BY created_at DESC, id DESC", mapper);
    }

    public int countByEventId(Long eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evidence_item WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evidence_item", Integer.class);
        return count == null ? 0 : count;
    }

    public int deleteByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(articleIds.size(), "?"));
        return jdbcTemplate.update("DELETE FROM evidence_item WHERE article_id IN (" + placeholders + ")",
                articleIds.toArray());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
