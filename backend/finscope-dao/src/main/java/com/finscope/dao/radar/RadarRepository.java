package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RadarRepository {
    private final JdbcTemplate jdbc;

    public RadarRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RadarSignal capture(RadarSignal signal, LocalDateTime now) {
        String timestamp = TimeUtil.text(now);
        jdbc.update("INSERT INTO radar_signal(item_id,provider_code,source_name,source_tier,category_code,title,content,url,"
                        + "published_at,first_seen_at,last_seen_at,content_hash,status,source_rank,previous_source_rank,source_weight) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(item_id) DO UPDATE SET provider_code=COALESCE(excluded.provider_code,provider_code),"
                        + "source_name=COALESCE(excluded.source_name,source_name),source_tier=COALESCE(excluded.source_tier,source_tier),"
                        + "category_code=COALESCE(excluded.category_code,category_code),title=excluded.title,"
                        + "content=COALESCE(excluded.content,content),url=COALESCE(excluded.url,url),"
                        + "published_at=COALESCE(excluded.published_at,published_at),last_seen_at=excluded.last_seen_at,"
                        + "content_hash=excluded.content_hash,status='ACTIVE',"
                        + "previous_source_rank=COALESCE(excluded.previous_source_rank,source_rank),"
                        + "source_rank=COALESCE(excluded.source_rank,source_rank),"
                        + "source_weight=CASE WHEN excluded.source_weight=0 THEN source_weight ELSE excluded.source_weight END",
                signal.getItemId(), signal.getProviderCode(), signal.getSourceName(), signal.getSourceTier(),
                signal.getCategoryCode(), signal.getTitle(), signal.getContent(), signal.getUrl(),
                TimeUtil.text(signal.getPublishedAt()), timestamp, timestamp, signal.getContentHash(), "ACTIVE",
                signal.getSourceRank(), signal.getPreviousSourceRank(), signal.getSourceWeight());
        return findSignalByItemId(signal.getItemId()).orElseThrow(IllegalStateException::new);
    }

    public List<RadarSignal> findActiveSignals(LocalDateTime since, int limit) {
        return jdbc.query("SELECT * FROM radar_signal WHERE status='ACTIVE' AND last_seen_at>=? "
                        + "ORDER BY COALESCE(published_at,first_seen_at) DESC,id DESC LIMIT ?",
                signalMapper(), TimeUtil.text(since), Math.max(1, limit));
    }

    public Optional<RadarSignal> findSignalByItemId(String itemId) {
        List<RadarSignal> values = jdbc.query("SELECT * FROM radar_signal WHERE item_id=?", signalMapper(), itemId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public RadarEvent saveEvent(RadarEvent event) {
        jdbc.update("INSERT INTO radar_event(event_key,canonical_title,summary,category_code,status,first_seen_at,last_seen_at,"
                        + "source_count,signal_count,priority_score,score_explanation,watchlist_relevance,watchlist_explanation,"
                        + "uncertainty,next_observation,evidence_status,evidence_summary,evidence_warning,evidence_fingerprint,"
                        + "evidence_count,evidence_source_count,evidence_updated_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(event_key) DO UPDATE SET canonical_title=excluded.canonical_title,summary=excluded.summary,"
                        + "category_code=excluded.category_code,status=excluded.status,last_seen_at=excluded.last_seen_at,"
                        + "source_count=excluded.source_count,signal_count=excluded.signal_count,priority_score=excluded.priority_score,"
                        + "score_explanation=excluded.score_explanation,watchlist_relevance=excluded.watchlist_relevance,"
                        + "watchlist_explanation=excluded.watchlist_explanation,uncertainty=excluded.uncertainty,"
                        + "next_observation=excluded.next_observation,"
                        + "evidence_status=COALESCE(excluded.evidence_status,evidence_status),"
                        + "evidence_summary=CASE WHEN excluded.evidence_status IS NULL THEN evidence_summary ELSE excluded.evidence_summary END,"
                        + "evidence_warning=CASE WHEN excluded.evidence_status IS NULL THEN evidence_warning ELSE excluded.evidence_warning END,"
                        + "evidence_fingerprint=COALESCE(excluded.evidence_fingerprint,evidence_fingerprint),"
                        + "evidence_count=CASE WHEN excluded.evidence_status IS NULL THEN evidence_count ELSE excluded.evidence_count END,"
                        + "evidence_source_count=CASE WHEN excluded.evidence_status IS NULL THEN evidence_source_count ELSE excluded.evidence_source_count END,"
                        + "evidence_updated_at=CASE WHEN excluded.evidence_status IS NULL THEN evidence_updated_at ELSE excluded.evidence_updated_at END,"
                        + "updated_at=excluded.updated_at",
                event.getEventKey(), event.getCanonicalTitle(), event.getSummary(), event.getCategoryCode(), event.getStatus(),
                TimeUtil.text(event.getFirstSeenAt()), TimeUtil.text(event.getLastSeenAt()), event.getSourceCount(),
                event.getSignalCount(), event.getPriorityScore(), event.getScoreExplanation(), event.getWatchlistRelevance(),
                event.getWatchlistExplanation(), event.getUncertainty(), event.getNextObservation(), event.getEvidenceStatus(),
                event.getEvidenceSummary(), event.getEvidenceWarning(), event.getEvidenceFingerprint(), event.getEvidenceCount(),
                event.getEvidenceSourceCount(), TimeUtil.text(event.getEvidenceUpdatedAt()), TimeUtil.text(event.getUpdatedAt()));
        RadarEvent stored = jdbc.queryForObject("SELECT * FROM radar_event WHERE event_key=?", eventMapper(), event.getEventKey());
        return stored;
    }

    public void updateEvidenceEnhancement(RadarEvent event) {
        jdbc.update("UPDATE radar_event SET canonical_title=?,next_observation=?,evidence_status=?,evidence_summary=?,"
                        + "evidence_warning=?,evidence_fingerprint=?,evidence_count=?,evidence_source_count=?,"
                        + "evidence_updated_at=? WHERE id=? AND updated_at=?",
                event.getCanonicalTitle(), event.getNextObservation(), event.getEvidenceStatus(),
                event.getEvidenceSummary(), event.getEvidenceWarning(), event.getEvidenceFingerprint(),
                event.getEvidenceCount(), event.getEvidenceSourceCount(), TimeUtil.text(event.getEvidenceUpdatedAt()),
                event.getId(), TimeUtil.text(event.getUpdatedAt()));
    }

    public void expireEventsExcept(java.util.Set<String> activeEventKeys, LocalDateTime now) {
        if (activeEventKeys == null || activeEventKeys.isEmpty()) {
            jdbc.update("UPDATE radar_event SET status='EXPIRED',updated_at=? WHERE status IN ('ACTIVE','QUIET')",
                    TimeUtil.text(now));
            return;
        }
        StringBuilder placeholders = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        args.add(TimeUtil.text(now));
        for (String eventKey : activeEventKeys) {
            if (placeholders.length() > 0) placeholders.append(',');
            placeholders.append('?');
            args.add(eventKey);
        }
        jdbc.update("UPDATE radar_event SET status='EXPIRED',updated_at=? WHERE status IN ('ACTIVE','QUIET') "
                + "AND event_key NOT IN (" + placeholders + ")", args.toArray());
    }

    @Transactional
    public void replaceEventSignals(Long eventId, List<RadarEventSignal> links) {
        jdbc.update("DELETE FROM radar_event_signal WHERE event_id=?", eventId);
        for (RadarEventSignal link : links) {
            jdbc.update("INSERT INTO radar_event_signal(event_id,signal_id,relation_type,match_score,match_reason) VALUES(?,?,?,?,?)",
                    eventId, link.getSignalId(), link.getRelationType(), link.getMatchScore(), link.getMatchReason());
        }
    }

    public Optional<RadarEvent> findEvent(Long id) {
        List<RadarEvent> values = jdbc.query("SELECT * FROM radar_event WHERE id=?", eventMapper(), id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<RadarSignal> findSignalsByEventId(Long eventId) {
        return jdbc.query("SELECT s.* FROM radar_signal s JOIN radar_event_signal l ON l.signal_id=s.id "
                + "WHERE l.event_id=? ORDER BY COALESCE(s.published_at,s.first_seen_at) DESC", signalMapper(), eventId);
    }

    public List<RadarEventSignal> findEventSignals(Long eventId) {
        return jdbc.query("SELECT event_id,signal_id,relation_type,match_score,match_reason FROM radar_event_signal "
                        + "WHERE event_id=? ORDER BY signal_id",
                (rs, rowNum) -> { RadarEventSignal value = new RadarEventSignal(); value.setEventId(rs.getLong("event_id"));
                    value.setSignalId(rs.getLong("signal_id")); value.setRelationType(rs.getString("relation_type"));
                    value.setMatchScore(rs.getDouble("match_score")); value.setMatchReason(rs.getString("match_reason")); return value; }, eventId);
    }

    public List<RadarEvent> findRanked(String category, boolean watchlistOnly, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM radar_event WHERE status IN ('ACTIVE','QUIET')");
        List<Object> args = new ArrayList<Object>();
        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            sql.append(" AND category_code=?"); args.add(category.trim().toUpperCase());
        }
        if (watchlistOnly) sql.append(" AND watchlist_relevance>0");
        sql.append(" ORDER BY priority_score DESC,last_seen_at DESC LIMIT ?"); args.add(Math.max(1, limit));
        return jdbc.query(sql.toString(), eventMapper(), args.toArray());
    }

    public void expireSignals(LocalDateTime before, LocalDateTime now) {
        jdbc.update("UPDATE radar_signal SET status='EXPIRED',last_seen_at=last_seen_at WHERE last_seen_at<? AND status='ACTIVE'",
                TimeUtil.text(before));
    }

    private org.springframework.jdbc.core.RowMapper<RadarSignal> signalMapper() {
        return (rs, rowNum) -> { RadarSignal value = new RadarSignal(); value.setId(rs.getLong("id"));
            value.setItemId(rs.getString("item_id")); value.setProviderCode(rs.getString("provider_code"));
            value.setSourceName(rs.getString("source_name")); value.setSourceTier(rs.getString("source_tier"));
            value.setCategoryCode(rs.getString("category_code")); value.setTitle(rs.getString("title"));
            value.setContent(rs.getString("content")); value.setUrl(rs.getString("url"));
            value.setPublishedAt(TimeUtil.localDateTime(rs,"published_at")); value.setFirstSeenAt(TimeUtil.localDateTime(rs,"first_seen_at"));
            value.setLastSeenAt(TimeUtil.localDateTime(rs,"last_seen_at")); value.setContentHash(rs.getString("content_hash"));
            value.setStatus(rs.getString("status")); value.setSourceRank((Integer) rs.getObject("source_rank"));
            value.setPreviousSourceRank((Integer) rs.getObject("previous_source_rank")); value.setSourceWeight(rs.getDouble("source_weight"));
            return value; };
    }

    private org.springframework.jdbc.core.RowMapper<RadarEvent> eventMapper() {
        return (rs, rowNum) -> { RadarEvent value = new RadarEvent(); value.setId(rs.getLong("id"));
            value.setEventKey(rs.getString("event_key")); value.setCanonicalTitle(rs.getString("canonical_title"));
            value.setSummary(rs.getString("summary")); value.setCategoryCode(rs.getString("category_code"));
            value.setStatus(rs.getString("status")); value.setFirstSeenAt(TimeUtil.localDateTime(rs,"first_seen_at"));
            value.setLastSeenAt(TimeUtil.localDateTime(rs,"last_seen_at")); value.setSourceCount(rs.getInt("source_count"));
            value.setSignalCount(rs.getInt("signal_count")); value.setPriorityScore(rs.getInt("priority_score"));
            value.setScoreExplanation(rs.getString("score_explanation")); value.setWatchlistRelevance(rs.getInt("watchlist_relevance"));
            value.setWatchlistExplanation(rs.getString("watchlist_explanation")); value.setUncertainty(rs.getString("uncertainty"));
            value.setNextObservation(rs.getString("next_observation")); value.setEvidenceStatus(rs.getString("evidence_status"));
            value.setEvidenceSummary(rs.getString("evidence_summary")); value.setEvidenceWarning(rs.getString("evidence_warning"));
            value.setEvidenceFingerprint(rs.getString("evidence_fingerprint")); value.setEvidenceCount(rs.getInt("evidence_count"));
            value.setEvidenceSourceCount(rs.getInt("evidence_source_count"));
            value.setEvidenceUpdatedAt(TimeUtil.localDateTime(rs,"evidence_updated_at"));
            value.setUpdatedAt(TimeUtil.localDateTime(rs,"updated_at")); return value; };
    }
}
