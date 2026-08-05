package com.finscope.dao.radar;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.radar.RadarEventSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RadarEventSnapshotRepository {
    private final JdbcTemplate jdbc;

    public RadarEventSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RadarEventSnapshot save(RadarEventSnapshot snapshot) {
        jdbc.update("INSERT INTO radar_event_snapshot(event_id,snapshot_at,signal_count,independent_source_count,"
                        + "velocity_score,hotness_score,lifecycle_state,explanation) VALUES(?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(event_id,snapshot_at) DO UPDATE SET signal_count=excluded.signal_count,"
                        + "independent_source_count=excluded.independent_source_count,velocity_score=excluded.velocity_score,"
                        + "hotness_score=excluded.hotness_score,lifecycle_state=excluded.lifecycle_state,explanation=excluded.explanation",
                snapshot.getEventId(), TimeUtil.text(snapshot.getSnapshotAt()), snapshot.getSignalCount(),
                snapshot.getIndependentSourceCount(), snapshot.getVelocityScore(), snapshot.getHotnessScore(),
                snapshot.getLifecycleState(), snapshot.getExplanation());
        return jdbc.queryForObject("SELECT * FROM radar_event_snapshot WHERE event_id=? AND snapshot_at=?",
                mapper(), snapshot.getEventId(), TimeUtil.text(snapshot.getSnapshotAt()));
    }

    public Optional<RadarEventSnapshot> findLatestBefore(Long eventId, LocalDateTime before) {
        List<RadarEventSnapshot> values = jdbc.query("SELECT * FROM radar_event_snapshot WHERE event_id=? AND snapshot_at<? "
                        + "ORDER BY snapshot_at DESC,id DESC LIMIT 1", mapper(), eventId, TimeUtil.text(before));
        return values.isEmpty() ? Optional.<RadarEventSnapshot>empty() : Optional.of(values.get(0));
    }

    private org.springframework.jdbc.core.RowMapper<RadarEventSnapshot> mapper() {
        return (rs, rowNum) -> {
            RadarEventSnapshot value = new RadarEventSnapshot();
            value.setId(rs.getLong("id")); value.setEventId(rs.getLong("event_id"));
            value.setSnapshotAt(TimeUtil.localDateTime(rs, "snapshot_at")); value.setSignalCount(rs.getInt("signal_count"));
            value.setIndependentSourceCount(rs.getInt("independent_source_count")); value.setVelocityScore(rs.getDouble("velocity_score"));
            value.setHotnessScore(rs.getInt("hotness_score")); value.setLifecycleState(rs.getString("lifecycle_state"));
            value.setExplanation(rs.getString("explanation")); return value;
        };
    }
}
