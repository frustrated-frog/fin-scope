package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarEventInterpretation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.finscope.common.exception.BizErrorCode;

@Repository
public class RadarEventInterpretationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RadarEventInterpretationRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public RadarEventInterpretation saveQueued(Long eventId, String fingerprint) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO radar_event_interpretation(event_id,event_fingerprint,status,created_at) "
                        + "VALUES(?,?,'QUEUED',?) ON CONFLICT(event_id,event_fingerprint) DO NOTHING",
                eventId, fingerprint, TimeUtil.text(now));
        return findByEventFingerprint(eventId, fingerprint)
                .orElseThrow(() -> new IllegalStateException("cannot persist radar event interpretation"));
    }

    public void update(RadarEventInterpretation value) {
        try {
            jdbc.update("UPDATE radar_event_interpretation SET status=?,result_json=?,failure_code=?,failure_message=?,"
                            + "duration_ms=?,started_at=?,completed_at=? WHERE id=?",
                    value.getStatus(), value.getResult() == null ? null : json.writeValueAsString(value.getResult()),
                    value.getFailureCode(), value.getFailureMessage(), value.getDurationMs(),
                    TimeUtil.text(value.getStartedAt()), TimeUtil.text(value.getCompletedAt()), value.getId());
        } catch (Exception error) {
            throw new BusinessException(BizErrorCode.RADAR_INTERPRETATION_UPDATE_FAILED, BizErrorCode.RADAR_INTERPRETATION_UPDATE_FAILED.format(value.getId()), error);
        }
    }

    public Optional<RadarEventInterpretation> findByEventFingerprint(Long eventId, String fingerprint) {
        List<RadarEventInterpretation> rows = jdbc.query("SELECT * FROM radar_event_interpretation "
                + "WHERE event_id=? AND event_fingerprint=?", this::map, eventId, fingerprint);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<RadarEventInterpretation> findLatestByEventId(Long eventId) {
        List<RadarEventInterpretation> rows = jdbc.query("SELECT * FROM radar_event_interpretation "
                + "WHERE event_id=? ORDER BY id DESC LIMIT 1", this::map, eventId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<RadarEventInterpretation> findHistory(Long eventId, int limit) {
        return jdbc.query("SELECT * FROM radar_event_interpretation WHERE event_id=? ORDER BY id DESC LIMIT ?",
                this::map, eventId, Math.max(1, Math.min(limit, 100)));
    }

    public Map<Long, RadarEventInterpretation> findLatestByEventIds(List<Long> eventIds) {
        Map<Long, RadarEventInterpretation> result = new LinkedHashMap<Long, RadarEventInterpretation>();
        if (eventIds == null || eventIds.isEmpty()) return result;
        StringBuilder placeholders = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        for (Long eventId : eventIds) {
            if (placeholders.length() > 0) placeholders.append(',');
            placeholders.append('?'); args.add(eventId);
        }
        String sql = "SELECT value.* FROM radar_event_interpretation value JOIN "
                + "(SELECT event_id,MAX(id) latest_id FROM radar_event_interpretation WHERE event_id IN ("
                + placeholders + ") GROUP BY event_id) latest ON latest.latest_id=value.id";
        for (RadarEventInterpretation value : jdbc.query(sql, this::map, args.toArray())) {
            result.put(value.getEventId(), value);
        }
        return result;
    }

    private RadarEventInterpretation map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        try {
            RadarEventInterpretation value = new RadarEventInterpretation();
            value.setId(rs.getLong("id")); value.setEventId(rs.getLong("event_id"));
            value.setEventFingerprint(rs.getString("event_fingerprint")); value.setStatus(rs.getString("status"));
            String resultJson = rs.getString("result_json");
            value.setResult(resultJson == null ? null : json.readValue(resultJson, RadarEventInterpretation.Result.class));
            value.setFailureCode(rs.getString("failure_code")); value.setFailureMessage(rs.getString("failure_message"));
            Object duration = rs.getObject("duration_ms"); value.setDurationMs(duration == null ? null : rs.getLong("duration_ms"));
            value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
            value.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
            value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at"));
            return value;
        } catch (Exception error) {
            throw new BusinessException(BizErrorCode.RADAR_INTERPRETATION_READ_FAILED, error);
        }
    }
}
