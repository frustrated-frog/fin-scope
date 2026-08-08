package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarSignalStatus;
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
                TimeUtil.text(signal.getPublishedAt()), timestamp, timestamp, signal.getContentHash(),
                RadarSignalStatus.ACTIVE.code(), signal.getSourceRank(), signal.getPreviousSourceRank(),
                signal.getSourceWeight());
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
        jdbc.update("INSERT INTO radar_event(event_key,canonical_title,summary,category_code,dashboard_category,status,first_seen_at,last_seen_at,"
                        + "source_count,signal_count,hotspot_score,hotspot_explanation,priority_score,score_explanation,watchlist_relevance,watchlist_explanation,"
                        + "uncertainty,next_observation,hotspot_lifecycle_state,evidence_status,evidence_summary,evidence_warning,evidence_fingerprint,"
                        + "evidence_count,evidence_source_count,evidence_updated_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(event_key) DO UPDATE SET canonical_title=excluded.canonical_title,summary=excluded.summary,"
                        + "category_code=excluded.category_code,dashboard_category=excluded.dashboard_category,status=excluded.status,last_seen_at=excluded.last_seen_at,"
                        + "source_count=excluded.source_count,signal_count=excluded.signal_count,hotspot_score=excluded.hotspot_score,"
                        + "hotspot_explanation=excluded.hotspot_explanation,priority_score=excluded.priority_score,"
                        + "hotspot_lifecycle_state=excluded.hotspot_lifecycle_state,"
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
                event.getEventKey(), event.getCanonicalTitle(), event.getSummary(), event.getCategoryCode(), event.getDashboardCategory(), event.getStatus(),
                TimeUtil.text(event.getFirstSeenAt()), TimeUtil.text(event.getLastSeenAt()), event.getSourceCount(),
                event.getSignalCount(), event.getHotspotScore(), event.getHotspotExplanation(), event.getPriorityScore(), event.getScoreExplanation(), event.getWatchlistRelevance(),
                event.getWatchlistExplanation(), event.getUncertainty(), event.getNextObservation(), event.getHotspotLifecycleState(), event.getEvidenceStatus(),
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

    public void expireEventsExcept(java.util.Set<String> activeEventKeys, LocalDateTime windowStart, LocalDateTime now) {
        boolean filterKeys = activeEventKeys != null && !activeEventKeys.isEmpty();
        StringBuilder placeholders = new StringBuilder();
        List<Object> keys = new ArrayList<Object>();
        if (filterKeys) {
            for (String eventKey : activeEventKeys) {
                if (placeholders.length() > 0) placeholders.append(',');
                placeholders.append('?');
                keys.add(eventKey);
            }
        }
        String notIn = filterKeys ? " AND event_key NOT IN (" + placeholders + ")" : "";
        // 仍在聚合窗口内但本轮没有新信号 → QUIET 保留，后续重新出现信号时回到 ACTIVE，离开窗口后才 EXPIRED。
        List<Object> quietParams = new ArrayList<Object>();
        quietParams.add(TimeUtil.text(now));
        quietParams.addAll(keys);
        quietParams.add(TimeUtil.text(windowStart));
        jdbc.update("UPDATE radar_event SET status='QUIET',updated_at=? WHERE status IN ('ACTIVE','QUIET') "
                + notIn + " AND COALESCE(last_seen_at,updated_at)>=?", quietParams.toArray());
        // 离开聚合窗口 → EXPIRED，不再参与雷达展示。
        List<Object> expiredParams = new ArrayList<Object>();
        expiredParams.add(TimeUtil.text(now));
        expiredParams.addAll(keys);
        expiredParams.add(TimeUtil.text(windowStart));
        jdbc.update("UPDATE radar_event SET status='EXPIRED',updated_at=? WHERE status IN ('ACTIVE','QUIET') "
                + notIn + " AND COALESCE(last_seen_at,updated_at)<?", expiredParams.toArray());
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

    public Optional<RadarEvent> findEventByKey(String eventKey) {
        List<RadarEvent> values = jdbc.query("SELECT * FROM radar_event WHERE event_key=?", eventMapper(), eventKey);
        return values.isEmpty() ? Optional.<RadarEvent>empty() : Optional.of(values.get(0));
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
        StringBuilder filters = new StringBuilder(" WHERE e.status IN ('ACTIVE','QUIET')");
        List<Object> args = new ArrayList<Object>();
        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            filters.append(" AND e.category_code=?"); args.add(category.trim().toUpperCase());
        }
        if (watchlistOnly) filters.append(" AND e.watchlist_relevance>0");
        args.add(normalizeLimit(limit));
        return jdbc.query(deduplicatedEventsSql(filters.toString(), "e.priority_score DESC,e.hotspot_score DESC,e.last_seen_at DESC,e.id DESC"),
                eventMapper(), args.toArray());
    }

    public List<RadarEvent> findTopByDashboardCategory(String dashboardCategory, int limit) {
        return jdbc.query(deduplicatedEventsSql(" WHERE e.status IN ('ACTIVE','QUIET') AND e.dashboard_category=?",
                        "e.hotspot_score DESC,e.last_seen_at DESC,e.id DESC"),
                eventMapper(), dashboardCategory, normalizeLimit(limit));
    }

    public List<RadarEvent> findEventsMissingDashboardCategory(int limit) {
        return jdbc.query("SELECT * FROM radar_event WHERE status IN ('ACTIVE','QUIET') "
                        + "AND (dashboard_category IS NULL OR dashboard_category='' OR dashboard_category='UNCLASSIFIED') "
                        + "ORDER BY last_seen_at DESC,id DESC LIMIT ?",
                eventMapper(), Math.max(1, Math.min(limit, 1000)));
    }

    public List<RadarEvent> findEventsForDashboardClassification(int limit) {
        return jdbc.query("SELECT * FROM radar_event WHERE status IN ('ACTIVE','QUIET') "
                        + "ORDER BY last_seen_at DESC,id DESC LIMIT ?", eventMapper(),
                Math.max(1, Math.min(limit, 1000)));
    }

    /**
     * 分类码修复前生成的事件键包含分类前缀，同一标题可能因此留下两个可展示事件。
     * 生产写入后将旧副本标记过期，保留事件历史和关联证据，但不再进入雷达榜单。
     */
    public void expireDuplicateEventsByCanonicalTitle(String canonicalTitle, Long keepEventId, LocalDateTime now) {
        if (canonicalTitle == null || canonicalTitle.trim().isEmpty() || keepEventId == null) return;
        jdbc.update("UPDATE radar_event SET status='EXPIRED',updated_at=? WHERE status IN ('ACTIVE','QUIET') "
                        + "AND id<>? AND lower(trim(canonical_title))=lower(trim(?))",
                TimeUtil.text(now), keepEventId, canonicalTitle);
    }

    private String deduplicatedEventsSql(String filters, String orderBy) {
        String legacyCategoryPrefix = "CASE WHEN substr(e.event_key,1,instr(e.event_key,':')-1) "
                + "IN ('COMPANY','INDUSTRY','MARKET_MOVE','MACRO_POLICY','GLOBAL','UNCLASSIFIED') THEN 1 ELSE 0 END";
        String titleKey = "CASE WHEN " + legacyCategoryPrefix + "=1 THEN "
                + "COALESCE(NULLIF(lower(trim(e.canonical_title)),''),'legacy:' || e.id) ELSE 'event:' || e.id END";
        return "SELECT * FROM (SELECT e.*,ROW_NUMBER() OVER (PARTITION BY " + titleKey + " ORDER BY "
                + legacyCategoryPrefix + " ASC," + orderBy + ") duplicate_rank FROM radar_event e" + filters
                + ") WHERE duplicate_rank=1 ORDER BY " + orderBy.replace("e.", "") + " LIMIT ?";
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    public void updateDashboardCategory(Long eventId, String dashboardCategory) {
        jdbc.update("UPDATE radar_event SET dashboard_category=? WHERE id=?", dashboardCategory, eventId);
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
            value.setDashboardCategory(rs.getString("dashboard_category"));
            value.setStatus(rs.getString("status")); value.setFirstSeenAt(TimeUtil.localDateTime(rs,"first_seen_at"));
            value.setLastSeenAt(TimeUtil.localDateTime(rs,"last_seen_at")); value.setSourceCount(rs.getInt("source_count"));
            value.setSignalCount(rs.getInt("signal_count")); value.setHotspotScore(rs.getInt("hotspot_score"));
            value.setHotspotExplanation(rs.getString("hotspot_explanation")); value.setHotspotLifecycleState(rs.getString("hotspot_lifecycle_state"));
            value.setPriorityScore(rs.getInt("priority_score"));
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
