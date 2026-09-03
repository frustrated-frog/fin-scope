package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class RadarEventSnapshotRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarEventSnapshot save(RadarEventSnapshot snapshot) {
        return store.update(state -> {
            snapshot.setId(store.stableId("snapshot", snapshot.getEventId() + ":" + snapshot.getSnapshotAt()));
            List<RadarEventSnapshot> values = state.getSnapshots().computeIfAbsent(snapshot.getEventId(),
                    ignored -> new ArrayList<RadarEventSnapshot>());
            values.removeIf(value -> snapshot.getSnapshotAt().equals(value.getSnapshotAt()));
            values.add(snapshot);
            return snapshot;
        });
    }

    public Optional<RadarEventSnapshot> findLatestBefore(Long eventId, LocalDateTime before) {
        return store.read().getSnapshots().getOrDefault(eventId, java.util.Collections.emptyList()).stream()
                .filter(value -> value.getSnapshotAt() != null && value.getSnapshotAt().isBefore(before))
                .max(Comparator.comparing(RadarEventSnapshot::getSnapshotAt).thenComparing(RadarEventSnapshot::getId));
    }

    public void deleteExpired(LocalDateTime keepAfter) {
        store.update(state -> {
            for (List<RadarEventSnapshot> values : state.getSnapshots().values()) {
                values.removeIf(value -> value.getSnapshotAt() == null || value.getSnapshotAt().isBefore(keepAfter));
            }
            return null;
        });
    }
}
