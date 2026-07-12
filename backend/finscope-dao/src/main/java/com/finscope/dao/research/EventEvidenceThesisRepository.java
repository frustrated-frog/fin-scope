package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.EventEvidenceThesis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.sql.PreparedStatement; import java.sql.Statement; import java.time.LocalDateTime; import java.util.List; import java.util.Optional;

@Repository
public class EventEvidenceThesisRepository {
    @Resource private JdbcTemplate jdbcTemplate;
    private final org.springframework.jdbc.core.RowMapper<EventEvidenceThesis> mapper = (rs, n) -> { EventEvidenceThesis t = new EventEvidenceThesis(); t.setId(rs.getLong("id")); t.setEventId(rs.getLong("event_id")); t.setStatement(rs.getString("statement")); t.setKind(rs.getString("kind")); t.setStatus(rs.getString("status")); t.setRationale(rs.getString("rationale")); t.setEvidenceGap(rs.getString("evidence_gap")); t.setCreatedAt(TimeUtil.localDateTime(rs,"created_at")); t.setUpdatedAt(TimeUtil.localDateTime(rs,"updated_at")); return t; };
    public Optional<EventEvidenceThesis> findByEventAndStatement(Long eventId, String statement) { List<EventEvidenceThesis> result = jdbcTemplate.query("SELECT * FROM event_evidence_thesis WHERE event_id=? AND statement=?", mapper, eventId, statement); return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0)); }
    public EventEvidenceThesis save(EventEvidenceThesis thesis) { LocalDateTime now = LocalDateTime.now(); thesis.setCreatedAt(now); thesis.setUpdatedAt(now); KeyHolder keys = new GeneratedKeyHolder(); jdbcTemplate.update(c -> { PreparedStatement ps=c.prepareStatement("INSERT INTO event_evidence_thesis(event_id,statement,kind,status,rationale,evidence_gap,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setLong(1,thesis.getEventId());ps.setString(2,thesis.getStatement());ps.setString(3,thesis.getKind());ps.setString(4,thesis.getStatus());ps.setString(5,thesis.getRationale());ps.setString(6,thesis.getEvidenceGap());ps.setString(7,TimeUtil.text(now));ps.setString(8,TimeUtil.text(now));return ps;},keys); thesis.setId(keys.getKey().longValue()); return thesis; }
    public void linkEvidence(Long thesisId, Long evidenceId) { jdbcTemplate.update("INSERT OR IGNORE INTO event_evidence_thesis_link(thesis_id,evidence_id) VALUES(?,?)", thesisId,evidenceId); }
}
