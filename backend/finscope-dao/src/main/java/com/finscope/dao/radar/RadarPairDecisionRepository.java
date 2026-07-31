package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarPairDecision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RadarPairDecisionRepository {
    private final JdbcTemplate jdbc;

    public RadarPairDecisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(RadarPairDecision value) {
        LocalDateTime updatedAt = value.getUpdatedAt() == null ? LocalDateTime.now() : value.getUpdatedAt();
        LocalDateTime createdAt = value.getCreatedAt() == null ? updatedAt : value.getCreatedAt();
        jdbc.update("INSERT INTO radar_pair_decision(pair_key,left_fingerprint,right_fingerprint,same_event,confidence,"
                        + "reason,decision_source,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(pair_key) DO UPDATE SET same_event=excluded.same_event,confidence=excluded.confidence,"
                        + "reason=excluded.reason,decision_source=excluded.decision_source,updated_at=excluded.updated_at",
                value.getPairKey(), value.getLeftFingerprint(), value.getRightFingerprint(),
                value.isSameEvent() ? 1 : 0, value.getConfidence(), value.getReason(), value.getDecisionSource(),
                TimeUtil.text(createdAt), TimeUtil.text(updatedAt));
    }

    public Optional<RadarPairDecision> find(String pairKey) {
        List<RadarPairDecision> values = jdbc.query("SELECT * FROM radar_pair_decision WHERE pair_key=?",
                (rs, rowNum) -> {
                    RadarPairDecision value = new RadarPairDecision();
                    value.setPairKey(rs.getString("pair_key"));
                    value.setLeftFingerprint(rs.getString("left_fingerprint"));
                    value.setRightFingerprint(rs.getString("right_fingerprint"));
                    value.setSameEvent(rs.getInt("same_event") == 1);
                    value.setConfidence(rs.getDouble("confidence"));
                    value.setReason(rs.getString("reason"));
                    value.setDecisionSource(rs.getString("decision_source"));
                    value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
                    value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
                    return value;
                }, pairKey);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
}
