package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EventClusterRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EventCluster> eventMapper = (rs, rowNum) -> {
        EventCluster event = new EventCluster();
        event.setId(rs.getLong("id"));
        event.setCanonicalTitle(rs.getString("canonical_title"));
        event.setCanonicalEventKey(rs.getString("canonical_event_key"));
        event.setThemeCode(rs.getString("theme_code"));
        event.setSummary(rs.getString("summary"));
        event.setStatus(rs.getString("status"));
        event.setFirstSeenAt(TimeUtil.localDateTime(rs, "first_seen_at"));
        event.setLastSeenAt(TimeUtil.localDateTime(rs, "last_seen_at"));
        event.setLastMeaningfulUpdateAt(TimeUtil.localDateTime(rs, "last_meaningful_update_at"));
        event.setImportanceScore(rs.getInt("importance_score"));
        event.setNoveltyState(rs.getString("novelty_state"));
        event.setEvidenceCount(rs.getInt("evidence_count"));
        event.setArticleCount(rs.getInt("article_count"));
        event.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        event.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return event;
    };

    private final RowMapper<EventArticleLink> linkMapper = (rs, rowNum) -> {
        EventArticleLink link = new EventArticleLink();
        link.setEventId(rs.getLong("event_id"));
        link.setArticleId(rs.getLong("article_id"));
        link.setRelationType(rs.getString("relation_type"));
        link.setMatchScore(rs.getDouble("match_score"));
        link.setNoveltyType(rs.getString("novelty_type"));
        link.setNoveltyReason(rs.getString("novelty_reason"));
        link.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        try {
            link.setArticleTitle(rs.getString("title"));
            link.setArticleUrl(rs.getString("url"));
        } catch (Exception ignored) {
            // Plain link queries do not join article fields.
        }
        return link;
    };

    public EventClusterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EventCluster save(EventCluster event) {
        LocalDateTime now = LocalDateTime.now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO event_cluster(canonical_title,canonical_event_key,theme_code,summary,status,"
                            + "first_seen_at,last_seen_at,last_meaningful_update_at,importance_score,novelty_state,"
                            + "evidence_count,article_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            bindEvent(ps, event);
            return ps;
        }, keyHolder);
        event.setId(keyHolder.getKey().longValue());
        return event;
    }

    public EventCluster update(EventCluster event) {
        event.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE event_cluster SET canonical_title=?, canonical_event_key=?, theme_code=?, summary=?, "
                        + "status=?, first_seen_at=?, last_seen_at=?, last_meaningful_update_at=?, importance_score=?, "
                        + "novelty_state=?, evidence_count=?, article_count=?, updated_at=? WHERE id=?",
                event.getCanonicalTitle(), event.getCanonicalEventKey(), event.getThemeCode(), event.getSummary(),
                event.getStatus(), TimeUtil.text(event.getFirstSeenAt()), TimeUtil.text(event.getLastSeenAt()),
                TimeUtil.text(event.getLastMeaningfulUpdateAt()), value(event.getImportanceScore()),
                event.getNoveltyState(), value(event.getEvidenceCount()), value(event.getArticleCount()),
                TimeUtil.text(event.getUpdatedAt()), event.getId());
        return findById(event.getId()).orElse(event);
    }

    public Optional<EventCluster> findById(Long id) {
        List<EventCluster> events = jdbcTemplate.query("SELECT * FROM event_cluster WHERE id = ?", eventMapper, id);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    public List<EventCluster> findAll() {
        return jdbcTemplate.query("SELECT * FROM event_cluster ORDER BY last_seen_at DESC, id DESC", eventMapper);
    }

    public List<EventCluster> findRecentByTheme(String themeCode, int limit) {
        return jdbcTemplate.query("SELECT * FROM event_cluster WHERE theme_code = ? "
                        + "ORDER BY last_seen_at DESC, id DESC LIMIT ?",
                eventMapper, themeCode, limit);
    }

    public void linkArticle(EventArticleLink link) {
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(LocalDateTime.now());
        }
        jdbcTemplate.update("INSERT OR REPLACE INTO event_article_link(event_id,article_id,relation_type,match_score,"
                        + "novelty_type,novelty_reason,created_at) VALUES(?,?,?,?,?,?,?)",
                link.getEventId(), link.getArticleId(), link.getRelationType(), link.getMatchScore(),
                link.getNoveltyType(), link.getNoveltyReason(), TimeUtil.text(link.getCreatedAt()));
    }

    public List<EventArticleLink> findLinksByEventId(Long eventId) {
        return jdbcTemplate.query("SELECT l.*, a.title, a.url FROM event_article_link l "
                        + "JOIN article a ON a.id = l.article_id WHERE l.event_id = ? ORDER BY l.created_at ASC",
                linkMapper, eventId);
    }

    public Optional<EventArticleLink> findByArticleId(Long articleId) {
        List<EventArticleLink> links = jdbcTemplate.query("SELECT * FROM event_article_link WHERE article_id = ?",
                linkMapper, articleId);
        return links.isEmpty() ? Optional.empty() : Optional.of(links.get(0));
    }

    public int countLinks(Long eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_article_link WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private void bindEvent(PreparedStatement ps, EventCluster event) throws java.sql.SQLException {
        int i = 1;
        ps.setString(i++, event.getCanonicalTitle());
        ps.setString(i++, event.getCanonicalEventKey());
        ps.setString(i++, event.getThemeCode());
        ps.setString(i++, event.getSummary());
        ps.setString(i++, event.getStatus());
        ps.setString(i++, TimeUtil.text(event.getFirstSeenAt()));
        ps.setString(i++, TimeUtil.text(event.getLastSeenAt()));
        ps.setString(i++, TimeUtil.text(event.getLastMeaningfulUpdateAt()));
        ps.setInt(i++, value(event.getImportanceScore()));
        ps.setString(i++, event.getNoveltyState());
        ps.setInt(i++, value(event.getEvidenceCount()));
        ps.setInt(i++, value(event.getArticleCount()));
        ps.setString(i++, TimeUtil.text(event.getCreatedAt()));
        ps.setString(i++, TimeUtil.text(event.getUpdatedAt()));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
