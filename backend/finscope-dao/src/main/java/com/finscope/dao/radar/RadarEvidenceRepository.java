package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RadarEvidenceRepository {
    private final JdbcTemplate jdbc;

    public RadarEvidenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void replaceForEvent(Long eventId, List<RadarEvidence> values) {
        jdbc.update("DELETE FROM radar_evidence WHERE event_id=?", eventId);
        if (values == null) return;
        for (RadarEvidence value : values) {
            jdbc.update("INSERT INTO radar_evidence(event_id,tool_code,evidence_type,title,summary,url,source_name,"
                            + "source_tier,published_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    eventId, value.getToolCode(), value.getEvidenceType(), value.getTitle(), value.getSummary(),
                    value.getUrl(), value.getSourceName(), value.getSourceTier(), TimeUtil.text(value.getPublishedAt()),
                    TimeUtil.text(value.getCreatedAt() == null ? LocalDateTime.now() : value.getCreatedAt()));
        }
    }

    public List<RadarEvidence> findByEventId(Long eventId) {
        return jdbc.query("SELECT * FROM radar_evidence WHERE event_id=? ORDER BY COALESCE(published_at,created_at) DESC,id DESC",
                (rs, rowNum) -> { RadarEvidence value = new RadarEvidence(); value.setId(rs.getLong("id"));
                    value.setEventId(rs.getLong("event_id")); value.setToolCode(rs.getString("tool_code"));
                    value.setEvidenceType(rs.getString("evidence_type")); value.setTitle(rs.getString("title"));
                    value.setSummary(rs.getString("summary")); value.setUrl(rs.getString("url"));
                    value.setSourceName(rs.getString("source_name")); value.setSourceTier(rs.getString("source_tier"));
                    value.setPublishedAt(TimeUtil.localDateTime(rs, "published_at"));
                    value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); return value; }, eventId);
    }
}
