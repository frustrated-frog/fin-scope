package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RadarEventWorkspaceRepository {
    private static final List<String> DISPOSITIONS = Arrays.asList("ACTIVE", "LATER", "IGNORED");
    private static final List<String> OBSERVATION_STATUSES = Arrays.asList("OPEN", "DONE");
    private final JdbcTemplate jdbc;

    public RadarEventWorkspaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RadarEventWorkspace.State updateState(Long eventId, boolean markRead, String disposition,
                                                  Boolean followed, String fingerprint) {
        requireEventId(eventId);
        if (disposition != null && !DISPOSITIONS.contains(disposition)) {
            throw new IllegalArgumentException("雷达事件处理状态不合法");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO radar_event_user_state(event_id,followed,disposition,updated_at) "
                        + "VALUES(?,0,'ACTIVE',?) ON CONFLICT(event_id) DO NOTHING",
                eventId, TimeUtil.text(now));
        StringBuilder sql = new StringBuilder("UPDATE radar_event_user_state SET updated_at=?");
        List<Object> args = new ArrayList<Object>(); args.add(TimeUtil.text(now));
        if (markRead) { sql.append(",read_at=?"); args.add(TimeUtil.text(now)); }
        if (disposition != null) { sql.append(",disposition=?"); args.add(disposition); }
        if (followed != null) { sql.append(",followed=?"); args.add(followed ? 1 : 0); }
        if (fingerprint != null) { sql.append(",last_viewed_fingerprint=?"); args.add(fingerprint); }
        sql.append(" WHERE event_id=?"); args.add(eventId);
        jdbc.update(sql.toString(), args.toArray());
        return findState(eventId);
    }

    public RadarEventWorkspace.State findState(Long eventId) {
        List<RadarEventWorkspace.State> rows = jdbc.query("SELECT * FROM radar_event_user_state WHERE event_id=?",
                this::mapState, eventId);
        if (!rows.isEmpty()) return rows.get(0);
        RadarEventWorkspace.State state = new RadarEventWorkspace.State(); state.setEventId(eventId); return state;
    }

    public Map<Long, RadarEventWorkspace.Summary> findSummaries(List<Long> eventIds) {
        Map<Long, RadarEventWorkspace.Summary> result = new LinkedHashMap<Long, RadarEventWorkspace.Summary>();
        if (eventIds == null || eventIds.isEmpty()) return result;
        StringBuilder idRows = new StringBuilder(); List<Object> args = new ArrayList<Object>();
        for (Long eventId : eventIds) {
            if (idRows.length() > 0) idRows.append(" UNION ALL ");
            idRows.append("SELECT ? event_id"); args.add(eventId);
            RadarEventWorkspace.Summary empty = new RadarEventWorkspace.Summary(); empty.setEventId(eventId);
            result.put(eventId, empty);
        }
        String sql = "SELECT ids.event_id,s.read_at,s.followed,s.disposition,s.last_viewed_fingerprint,s.updated_at,"
                + "COALESCE(o.total_count,0) observation_count,COALESCE(o.open_count,0) open_observation_count,"
                + "COALESCE(r.run_count,0) research_run_count,COALESCE(n.unread_count,0) unread_notification_count "
                + "FROM (" + idRows + ") ids "
                + "LEFT JOIN radar_event_user_state s ON s.event_id=ids.event_id "
                + "LEFT JOIN (SELECT event_id,COUNT(*) total_count,SUM(CASE WHEN status='OPEN' THEN 1 ELSE 0 END) open_count FROM radar_event_observation GROUP BY event_id) o ON o.event_id=ids.event_id "
                + "LEFT JOIN (SELECT event_id,COUNT(*) run_count FROM radar_event_research_link GROUP BY event_id) r ON r.event_id=ids.event_id "
                + "LEFT JOIN (SELECT event_id,COUNT(*) unread_count FROM radar_event_notification WHERE read_at IS NULL GROUP BY event_id) n ON n.event_id=ids.event_id";
        for (RadarEventWorkspace.Summary value : jdbc.query(sql, this::mapSummary, args.toArray())) {
            result.put(value.getEventId(), value);
        }
        return result;
    }

    public List<RadarEventWorkspace.Observation> ensureDefaultObservation(Long eventId, String content) {
        insertObservation(eventId, content, "SYSTEM"); return findObservations(eventId);
    }

    public RadarEventWorkspace.Observation addObservation(Long eventId, String content) {
        String normalized = normalizeObservation(content);
        insertObservation(eventId, content, "USER");
        return jdbc.query("SELECT * FROM radar_event_observation WHERE event_id=? AND normalized_content=? AND source='USER'",
                this::mapObservation, eventId, normalized).get(0);
    }

    public RadarEventWorkspace.Observation setObservationStatus(Long eventId, Long observationId, String status) {
        if (!OBSERVATION_STATUSES.contains(status)) throw new IllegalArgumentException("观察项状态不合法");
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("UPDATE radar_event_observation SET status=?,completed_at=?,updated_at=? WHERE id=? AND event_id=?",
                status, "DONE".equals(status) ? TimeUtil.text(now) : null, TimeUtil.text(now), observationId, eventId);
        if (updated == 0) throw new IllegalArgumentException("观察项不存在");
        return findObservation(eventId, observationId);
    }

    public void deleteObservation(Long eventId, Long observationId) {
        RadarEventWorkspace.Observation value = findObservation(eventId, observationId);
        if (!"USER".equals(value.getSource())) throw new IllegalArgumentException("系统观察项不能删除");
        jdbc.update("DELETE FROM radar_event_observation WHERE id=? AND event_id=?", observationId, eventId);
    }

    public List<RadarEventWorkspace.Observation> findObservations(Long eventId) {
        return jdbc.query("SELECT * FROM radar_event_observation WHERE event_id=? ORDER BY status ASC,created_at ASC,id ASC",
                this::mapObservation, eventId);
    }

    public void appendTimeline(Long eventId, String eventFingerprint, String eventType, String title,
                               String summary, String referenceType, Long referenceId, LocalDateTime occurredAt) {
        requireEventId(eventId); LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO radar_event_timeline(event_id,event_fingerprint,event_type,title,summary,reference_type,reference_id,occurred_at,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(event_id,event_fingerprint,event_type,reference_type,reference_id) DO NOTHING",
                eventId, eventFingerprint, eventType, title, summary, referenceType, referenceId,
                TimeUtil.text(occurredAt == null ? now : occurredAt), TimeUtil.text(now));
    }

    public List<RadarEventWorkspace.TimelineEntry> findTimeline(Long eventId) {
        return jdbc.query("SELECT * FROM radar_event_timeline WHERE event_id=? ORDER BY occurred_at DESC,id DESC",
                this::mapTimeline, eventId);
    }

    public RadarEventWorkspace.ResearchLink linkResearchRun(Long eventId, Long researchRunId, String questionSnapshot) {
        requireEventId(eventId); LocalDateTime now=LocalDateTime.now();
        jdbc.update("INSERT INTO radar_event_research_link(event_id,research_run_id,question_snapshot,created_at) VALUES(?,?,?,?) "
                        + "ON CONFLICT(event_id,research_run_id) DO NOTHING",
                eventId,researchRunId,questionSnapshot==null?null:questionSnapshot.trim(),TimeUtil.text(now));
        return jdbc.query(researchLinkSql()+" WHERE l.event_id=? AND l.research_run_id=?",this::mapResearchLink,eventId,researchRunId).get(0);
    }

    public List<RadarEventWorkspace.ResearchLink> findResearchLinks(Long eventId) {
        return jdbc.query(researchLinkSql()+" WHERE l.event_id=? ORDER BY l.created_at DESC,l.id DESC",this::mapResearchLink,eventId);
    }

    private String researchLinkSql() {
        return "SELECT l.*,r.status research_status,r.summary research_summary FROM radar_event_research_link l "
                + "JOIN research_run r ON r.id=l.research_run_id";
    }

    private void insertObservation(Long eventId, String content, String source) {
        requireEventId(eventId); String value = validateObservation(content); String normalized = normalizeObservation(value);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO radar_event_observation(event_id,content,normalized_content,status,source,created_at,updated_at) "
                        + "VALUES(?,?,?,'OPEN',?,?,?) ON CONFLICT(event_id,normalized_content,source) DO NOTHING",
                eventId, value, normalized, source, TimeUtil.text(now), TimeUtil.text(now));
    }

    private RadarEventWorkspace.Observation findObservation(Long eventId, Long observationId) {
        List<RadarEventWorkspace.Observation> rows = jdbc.query(
                "SELECT * FROM radar_event_observation WHERE event_id=? AND id=?", this::mapObservation, eventId, observationId);
        if (rows.isEmpty()) throw new IllegalArgumentException("观察项不存在"); return rows.get(0);
    }

    private RadarEventWorkspace.State mapState(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        RadarEventWorkspace.State value = new RadarEventWorkspace.State();
        value.setEventId(rs.getLong("event_id")); value.setReadAt(TimeUtil.localDateTime(rs, "read_at"));
        value.setFollowed(rs.getInt("followed") == 1); value.setDisposition(rs.getString("disposition"));
        value.setLastViewedFingerprint(rs.getString("last_viewed_fingerprint"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at")); return value;
    }

    private RadarEventWorkspace.Summary mapSummary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        RadarEventWorkspace.Summary value = new RadarEventWorkspace.Summary(); value.setEventId(rs.getLong("event_id"));
        value.setReadAt(TimeUtil.localDateTime(rs, "read_at")); value.setFollowed(rs.getInt("followed") == 1);
        value.setDisposition(rs.getString("disposition") == null ? "ACTIVE" : rs.getString("disposition"));
        value.setLastViewedFingerprint(rs.getString("last_viewed_fingerprint"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        value.setObservationCount(rs.getInt("observation_count")); value.setOpenObservationCount(rs.getInt("open_observation_count"));
        value.setResearchRunCount(rs.getInt("research_run_count")); value.setUnreadNotificationCount(rs.getInt("unread_notification_count"));
        return value;
    }

    private RadarEventWorkspace.Observation mapObservation(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        RadarEventWorkspace.Observation value = new RadarEventWorkspace.Observation();
        value.setId(rs.getLong("id")); value.setEventId(rs.getLong("event_id")); value.setContent(rs.getString("content"));
        value.setStatus(rs.getString("status")); value.setSource(rs.getString("source"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at")); return value;
    }

    private RadarEventWorkspace.TimelineEntry mapTimeline(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        RadarEventWorkspace.TimelineEntry value = new RadarEventWorkspace.TimelineEntry();
        value.setId(rs.getLong("id")); value.setEventId(rs.getLong("event_id")); value.setEventType(rs.getString("event_type"));
        value.setTitle(rs.getString("title")); value.setSummary(rs.getString("summary"));
        value.setReferenceType(rs.getString("reference_type")); value.setReferenceId(rs.getLong("reference_id"));
        value.setOccurredAt(TimeUtil.localDateTime(rs, "occurred_at")); return value;
    }

    private RadarEventWorkspace.ResearchLink mapResearchLink(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        RadarEventWorkspace.ResearchLink value=new RadarEventWorkspace.ResearchLink();
        value.setId(rs.getLong("id"));value.setEventId(rs.getLong("event_id"));value.setResearchRunId(rs.getLong("research_run_id"));
        value.setQuestionSnapshot(rs.getString("question_snapshot"));value.setStatus(rs.getString("research_status"));
        value.setSummary(rs.getString("research_summary"));value.setCreatedAt(TimeUtil.localDateTime(rs,"created_at"));return value;
    }

    private String validateObservation(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("观察项不能为空");
        if (result.length() > 300) throw new IllegalArgumentException("观察项不能超过300字"); return result;
    }

    private String normalizeObservation(String value) {
        return validateObservation(value).replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private void requireEventId(Long eventId) {
        if (eventId == null || eventId <= 0) throw new IllegalArgumentException("雷达事件不能为空");
    }
}
