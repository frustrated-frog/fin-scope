package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class EventClusterRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

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

    public EventCluster updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE event_cluster SET status = ?, updated_at = ? WHERE id = ?",
                status, TimeUtil.text(LocalDateTime.now()), id);
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
    }

    public Optional<EventCluster> findById(Long id) {
        List<EventCluster> events = jdbcTemplate.query("SELECT * FROM event_cluster WHERE id = ?", eventMapper, id);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    public List<EventCluster> findByIds(List<Long> ids, int limit) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid event context limit");
        }
        List<Object> arguments = new ArrayList<Object>(ids);
        arguments.add(limit);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM event_cluster WHERE id IN (" + placeholders + ") " +
                "ORDER BY last_meaningful_update_at DESC,id DESC LIMIT ?", eventMapper, arguments.toArray());
    }

    public List<EventCluster> findAll() {
        return jdbcTemplate.query("SELECT * FROM event_cluster ORDER BY last_seen_at DESC, id DESC", eventMapper);
    }

    public List<EventCluster> findAllFiltered(String themeCode,
                                              String status,
                                              String noveltyState,
                                              LocalDate dateFrom,
                                              LocalDate dateTo) {
        StringBuilder sql = new StringBuilder("SELECT * FROM event_cluster WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (!isBlank(themeCode)) {
            sql.append(" AND lower(theme_code) = lower(?)");
            args.add(themeCode.trim());
        }
        if (!isBlank(status)) {
            sql.append(" AND lower(status) = lower(?)");
            args.add(status.trim());
        }
        if (!isBlank(noveltyState)) {
            sql.append(" AND lower(novelty_state) = lower(?)");
            args.add(noveltyState.trim());
        }
        if (dateFrom != null) {
            sql.append(" AND last_seen_at >= ?");
            args.add(dateFrom.toString() + "T00:00:00");
        }
        if (dateTo != null) {
            sql.append(" AND last_seen_at <= ?");
            args.add(dateTo.toString() + "T23:59:59");
        }
        sql.append(" ORDER BY last_seen_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), eventMapper, args.toArray());
    }

    public List<EventCluster> findFilteredPage(String themeCode, String status, String noveltyState, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize) {
        Filter filter = filter(themeCode, status, noveltyState, dateFrom, dateTo);
        filter.sql.append(" ORDER BY last_seen_at DESC, id DESC LIMIT ? OFFSET ?"); filter.args.add(pageSize); filter.args.add(page * pageSize);
        return jdbcTemplate.query(filter.sql.toString(), eventMapper, filter.args.toArray());
    }

    public int countFiltered(String themeCode, String status, String noveltyState, LocalDate dateFrom, LocalDate dateTo) {
        Filter filter = filter(themeCode, status, noveltyState, dateFrom, dateTo);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_cluster" + filter.sql.substring(filter.sql.indexOf(" WHERE")), Integer.class, filter.args.toArray());
        return count == null ? 0 : count;
    }

    private Filter filter(String themeCode, String status, String noveltyState, LocalDate dateFrom, LocalDate dateTo) {
        Filter filter = new Filter("SELECT * FROM event_cluster WHERE 1=1");
        if (!isBlank(themeCode)) { filter.sql.append(" AND lower(theme_code) = lower(?)"); filter.args.add(themeCode.trim()); }
        if (!isBlank(status)) { filter.sql.append(" AND lower(status) = lower(?)"); filter.args.add(status.trim()); }
        if (!isBlank(noveltyState)) { filter.sql.append(" AND lower(novelty_state) = lower(?)"); filter.args.add(noveltyState.trim()); }
        if (dateFrom != null) { filter.sql.append(" AND last_seen_at >= ?"); filter.args.add(dateFrom.toString() + "T00:00:00"); }
        if (dateTo != null) { filter.sql.append(" AND last_seen_at <= ?"); filter.args.add(dateTo.toString() + "T23:59:59"); }
        return filter;
    }

    private static class Filter { private final StringBuilder sql; private final List<Object> args = new ArrayList<Object>(); private Filter(String sql) { this.sql = new StringBuilder(sql); } }

    public List<EventCluster> findRecentByTheme(String themeCode, int limit) {
        return jdbcTemplate.query("SELECT * FROM event_cluster WHERE theme_code = ? "
                        + "ORDER BY last_seen_at DESC, id DESC LIMIT ?",
                eventMapper, themeCode, limit);
    }

    public List<EventCluster> findRecentMergeableByTheme(String themeCode, int limit) {
        return jdbcTemplate.query("SELECT * FROM event_cluster WHERE theme_code = ? "
                        + "AND status IN ('ACTIVE','COOLING') ORDER BY last_seen_at DESC, id DESC LIMIT ?",
                eventMapper, themeCode, limit);
    }

    public void linkArticle(EventArticleLink link) {
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(LocalDateTime.now());
        }
        jdbcTemplate.update("INSERT INTO event_article_link(event_id,article_id,relation_type,match_score,"
                        + "novelty_type,novelty_reason,created_at) VALUES(?,?,?,?,?,?,?) "
                        + "ON CONFLICT(article_id) DO NOTHING",
                link.getEventId(), link.getArticleId(), link.getRelationType(), link.getMatchScore(),
                link.getNoveltyType(), link.getNoveltyReason(), TimeUtil.text(link.getCreatedAt()));
    }

    public List<EventArticleLink> findLinksByEventId(Long eventId) {
        return jdbcTemplate.query("SELECT l.*, a.title, a.url FROM event_article_link l "
                        + "JOIN article a ON a.id = l.article_id WHERE l.event_id = ? ORDER BY l.created_at ASC",
                linkMapper, eventId);
    }

    public Optional<EventArticleLink> findByArticleId(Long articleId) {
        List<EventArticleLink> links = jdbcTemplate.query("SELECT * FROM event_article_link WHERE article_id = ? ORDER BY created_at ASC",
                linkMapper, articleId);
        return links.isEmpty() ? Optional.empty() : Optional.of(links.get(0));
    }

    public Optional<EventArticleLink> findLink(Long eventId, Long articleId) {
        List<EventArticleLink> links = jdbcTemplate.query("SELECT * FROM event_article_link WHERE event_id = ? AND article_id = ?",
                linkMapper, eventId, articleId);
        return links.isEmpty() ? Optional.empty() : Optional.of(links.get(0));
    }

    public int moveLinks(Long sourceEventId, Long targetEventId) {
        List<EventArticleLink> links = jdbcTemplate.query("SELECT * FROM event_article_link WHERE event_id = ?",
                linkMapper, sourceEventId);
        int moved = 0;
        for (EventArticleLink link : links) {
            link.setNoveltyReason(mergeReasons(link.getNoveltyReason(), "人工治理合并"));
            moved += moveLinkSafely(link, targetEventId);
        }
        return moved;
    }

    public int moveArticleLink(Long sourceEventId, Long articleId, Long targetEventId, String noveltyReason) {
        Optional<EventArticleLink> sourceLink = findLink(sourceEventId, articleId);
        if (!sourceLink.isPresent()) {
            return 0;
        }
        EventArticleLink link = sourceLink.get();
        link.setNoveltyReason(noveltyReason);
        return moveLinkSafely(link, targetEventId);
    }

    public int countLinks(Long eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_article_link WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_cluster", Integer.class);
        return count == null ? 0 : count;
    }

    public List<Long> findEventIdsByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(articleIds.size(), "?"));
        return jdbcTemplate.query("SELECT DISTINCT event_id FROM event_article_link WHERE article_id IN (" + placeholders + ")",
                (rs, rowNum) -> rs.getLong("event_id"), articleIds.toArray());
    }

    public int deleteLinksByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(articleIds.size(), "?"));
        return jdbcTemplate.update("DELETE FROM event_article_link WHERE article_id IN (" + placeholders + ")",
                articleIds.toArray());
    }

    public void refreshCounts(List<Long> eventIds) {
        for (Long eventId : unique(eventIds)) {
            jdbcTemplate.update("UPDATE event_cluster SET article_count = "
                            + "(SELECT COUNT(*) FROM event_article_link WHERE event_id = ?), "
                            + "evidence_count = (SELECT COUNT(*) FROM evidence_item WHERE event_id = ?), "
                            + "updated_at = ? WHERE id = ?",
                    eventId, eventId, TimeUtil.text(LocalDateTime.now()), eventId);
        }
    }

    public void archiveIfEmpty(List<Long> eventIds) {
        for (Long eventId : unique(eventIds)) {
            jdbcTemplate.update("UPDATE event_cluster SET status = 'ARCHIVED', updated_at = ? "
                            + "WHERE id = ? AND article_count = 0",
                    TimeUtil.text(LocalDateTime.now()), eventId);
        }
    }

    private List<Long> unique(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> unique = new ArrayList<Long>();
        for (Long eventId : eventIds) {
            if (eventId != null && !unique.contains(eventId)) {
                unique.add(eventId);
            }
        }
        return unique;
    }

    private int moveLinkSafely(EventArticleLink sourceLink, Long targetEventId) {
        Optional<EventArticleLink> existingTarget = findLink(targetEventId, sourceLink.getArticleId());
        if (!existingTarget.isPresent()) {
            return jdbcTemplate.update("UPDATE event_article_link SET event_id = ?, novelty_reason = ? "
                            + "WHERE event_id = ? AND article_id = ?",
                    targetEventId, sourceLink.getNoveltyReason(),
                    sourceLink.getEventId(), sourceLink.getArticleId());
        }

        EventArticleLink merged = mergeLinks(existingTarget.get(), sourceLink);
        updateLink(merged);
        deleteLink(sourceLink.getEventId(), sourceLink.getArticleId());
        return 1;
    }

    private EventArticleLink mergeLinks(EventArticleLink target, EventArticleLink source) {
        EventArticleLink merged = new EventArticleLink();
        merged.setEventId(target.getEventId());
        merged.setArticleId(target.getArticleId());
        merged.setRelationType(preferredRelationType(target.getRelationType(), source.getRelationType()));
        merged.setMatchScore(Math.max(value(target.getMatchScore()), value(source.getMatchScore())));
        merged.setNoveltyType(isBlank(target.getNoveltyType()) ? source.getNoveltyType() : target.getNoveltyType());
        merged.setNoveltyReason(mergeReasons(target.getNoveltyReason(), source.getNoveltyReason()));
        merged.setCreatedAt(earlier(target.getCreatedAt(), source.getCreatedAt()));
        return merged;
    }

    private void updateLink(EventArticleLink link) {
        jdbcTemplate.update("UPDATE event_article_link SET relation_type = ?, match_score = ?, novelty_type = ?, "
                        + "novelty_reason = ?, created_at = ? WHERE event_id = ? AND article_id = ?",
                link.getRelationType(), value(link.getMatchScore()), link.getNoveltyType(),
                link.getNoveltyReason(), TimeUtil.text(link.getCreatedAt()),
                link.getEventId(), link.getArticleId());
    }

    private int deleteLink(Long eventId, Long articleId) {
        return jdbcTemplate.update("DELETE FROM event_article_link WHERE event_id = ? AND article_id = ?",
                eventId, articleId);
    }

    private String preferredRelationType(String targetRelationType, String sourceRelationType) {
        if ("PRIMARY".equals(targetRelationType) || "PRIMARY".equals(sourceRelationType)) {
            return "PRIMARY";
        }
        return isBlank(targetRelationType) ? sourceRelationType : targetRelationType;
    }

    private String mergeReasons(String first, String second) {
        if (isBlank(first)) {
            return second;
        }
        if (isBlank(second) || first.contains(second)) {
            return first;
        }
        return first + "；" + second;
    }

    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }
}
