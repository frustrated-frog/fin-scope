package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.EvidenceItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class EvidenceItemRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<EvidenceItem> mapper = (rs, rowNum) -> {
        EvidenceItem item = new EvidenceItem();
        item.setId(rs.getLong("id"));
        item.setEventId(rs.getLong("event_id"));
        item.setArticleId(rs.getLong("article_id"));
        item.setSourceTier(rs.getString("source_tier"));
        item.setEvidenceType(rs.getString("evidence_type"));
        item.setClaim(rs.getString("claim"));
        item.setClaimKey(rs.getString("claim_key"));
        item.setConfidence(rs.getInt("confidence"));
        item.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        try {
            item.setArticleTitle(rs.getString("article_title"));
            item.setArticleUrl(rs.getString("article_url"));
            item.setArticlePublishedAt(TimeUtil.localDateTime(rs, "article_published_at"));
        } catch (Exception ignored) {
            // Detail joins provide article provenance; write paths intentionally do not.
        }
        return item;
    };

    public EvidenceItem save(EvidenceItem item) {
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }
        item.setClaimKey(normalizeClaimKey(item.getClaimKey(), item.getClaim()));
        jdbcTemplate.update("INSERT INTO evidence_item(event_id,article_id,source_tier,evidence_type,claim,claim_key,confidence,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(event_id,article_id,claim_key) DO UPDATE SET "
                        + "confidence = MAX(evidence_item.confidence, excluded.confidence), "
                        + "source_tier = CASE WHEN excluded.confidence > evidence_item.confidence THEN excluded.source_tier ELSE evidence_item.source_tier END, "
                        + "evidence_type = CASE WHEN excluded.confidence > evidence_item.confidence THEN excluded.evidence_type ELSE evidence_item.evidence_type END",
                item.getEventId(), item.getArticleId(), item.getSourceTier(), item.getEvidenceType(), item.getClaim(),
                item.getClaimKey(), value(item.getConfidence()), TimeUtil.text(item.getCreatedAt()));
        return findByEventArticleClaim(item.getEventId(), item.getArticleId(), item.getClaimKey()).orElse(item);
    }

    public List<EvidenceItem> findByEventId(Long eventId) {
        return jdbcTemplate.query(provenanceSelect() + " WHERE e.event_id = ? ORDER BY e.created_at ASC, e.id ASC",
                mapper, eventId);
    }

    public List<EvidenceItem> findByEventIds(List<Long> eventIds, int limit) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Invalid evidence context limit");
        }
        List<Object> arguments = new ArrayList<Object>(eventIds);
        arguments.add(limit);
        String placeholders = String.join(",", Collections.nCopies(eventIds.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM evidence_item WHERE event_id IN (" + placeholders + ") " +
                "ORDER BY created_at DESC,id DESC LIMIT ?", mapper, arguments.toArray());
    }

    public java.util.Optional<EvidenceItem> findById(Long id) {
        List<EvidenceItem> items = jdbcTemplate.query("SELECT * FROM evidence_item WHERE id = ?", mapper, id);
        return items.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(items.get(0));
    }

    public List<EvidenceItem> findAll() {
        return jdbcTemplate.query(provenanceSelect() + " ORDER BY e.created_at DESC, e.id DESC", mapper);
    }

    public List<EvidenceItem> findFiltered(Long eventId, String sourceTier, String evidenceType, Integer minConfidence) {
        StringBuilder sql = new StringBuilder(provenanceSelect() + " WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (eventId != null) {
            sql.append(" AND e.event_id = ?");
            args.add(eventId);
        }
        if (!isBlank(sourceTier)) {
            sql.append(" AND lower(e.source_tier) = lower(?)");
            args.add(sourceTier.trim());
        }
        if (!isBlank(evidenceType)) {
            sql.append(" AND lower(e.evidence_type) = lower(?)");
            args.add(evidenceType.trim());
        }
        if (minConfidence != null) {
            sql.append(" AND e.confidence >= ?");
            args.add(minConfidence);
        }
        sql.append(" ORDER BY e.created_at DESC, e.id DESC");
        return jdbcTemplate.query(sql.toString(), mapper, args.toArray());
    }

    public List<EvidenceItem> findFilteredPage(Long eventId, String sourceTier, String evidenceType, Integer minConfidence, int page, int pageSize) {
        Filter filter = filter(eventId, sourceTier, evidenceType, minConfidence);
        filter.sql.append(" ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?");
        filter.args.add(pageSize); filter.args.add(page * pageSize);
        return jdbcTemplate.query(filter.sql.toString(), mapper, filter.args.toArray());
    }

    public int countFiltered(Long eventId, String sourceTier, String evidenceType, Integer minConfidence) {
        Filter filter = filter(eventId, sourceTier, evidenceType, minConfidence);
        String where = filter.sql.substring(filter.sql.indexOf(" WHERE"));
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evidence_item e" + where, Integer.class, filter.args.toArray());
        return count == null ? 0 : count;
    }

    private Filter filter(Long eventId, String sourceTier, String evidenceType, Integer minConfidence) {
        Filter filter = new Filter(provenanceSelect() + " WHERE 1=1");
        if (eventId != null) { filter.sql.append(" AND e.event_id = ?"); filter.args.add(eventId); }
        if (!isBlank(sourceTier)) { filter.sql.append(" AND lower(e.source_tier) = lower(?)"); filter.args.add(sourceTier.trim()); }
        if (!isBlank(evidenceType)) { filter.sql.append(" AND lower(e.evidence_type) = lower(?)"); filter.args.add(evidenceType.trim()); }
        if (minConfidence != null) { filter.sql.append(" AND e.confidence >= ?"); filter.args.add(minConfidence); }
        return filter;
    }

    private static class Filter { private final StringBuilder sql; private final List<Object> args = new ArrayList<Object>(); private Filter(String sql) { this.sql = new StringBuilder(sql); } }

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

    public int moveByEventId(Long sourceEventId, Long targetEventId) {
        List<EvidenceItem> items = findByEventId(sourceEventId);
        for (EvidenceItem item : items) {
            item.setEventId(targetEventId);
            save(item);
        }
        return jdbcTemplate.update("DELETE FROM evidence_item WHERE event_id = ?", sourceEventId);
    }

    public int moveByEventIdAndArticleId(Long sourceEventId, Long articleId, Long targetEventId) {
        List<EvidenceItem> items = jdbcTemplate.query("SELECT * FROM evidence_item WHERE event_id = ? AND article_id = ?", mapper,
                sourceEventId, articleId);
        for (EvidenceItem item : items) {
            item.setEventId(targetEventId);
            save(item);
        }
        return jdbcTemplate.update("DELETE FROM evidence_item WHERE event_id = ? AND article_id = ?", sourceEventId, articleId);
    }

    private java.util.Optional<EvidenceItem> findByEventArticleClaim(Long eventId, Long articleId, String claimKey) {
        List<EvidenceItem> items = jdbcTemplate.query("SELECT * FROM evidence_item WHERE event_id = ? AND article_id = ? AND claim_key = ?",
                mapper, eventId, articleId, claimKey);
        return items.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(items.get(0));
    }

    private String provenanceSelect() {
        return "SELECT e.*, a.title AS article_title, a.url AS article_url, a.published_at AS article_published_at "
                + "FROM evidence_item e LEFT JOIN article a ON a.id = e.article_id";
    }

    private String normalizeClaimKey(String claimKey, String claim) {
        String value = isBlank(claimKey) ? claim : claimKey;
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
