package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RadarEventSnapshotRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarEventSnapshot save(RadarEventSnapshot snapshot) {
        return store.update(state -> save(state, snapshot));
    }

    public void saveBatch(List<RadarEventSnapshot> snapshots) {
        for (int start = 0; start < snapshots.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<RadarEventSnapshot> batch = snapshots.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, snapshots.size()));
            store.update(state -> {
                List<Long> ids = batch.stream().map(RadarEventSnapshot::getEventId).toList();
                RedisRadarCacheStore.prefetch(state.getEvents(), ids);
                RedisRadarCacheStore.prefetch(state.getSnapshots(), ids);
                for (RadarEventSnapshot snapshot : batch) {
                    save(state, snapshot);
                }
                return null;
            });
        }
    }

    private RadarEventSnapshot save(RadarCacheState state, RadarEventSnapshot snapshot) {
        snapshot.setId(store.stableId("snapshot", snapshot.getEventId() + ":" + snapshot.getSnapshotAt()));
        List<RadarEventSnapshot> values = state.getSnapshots().computeIfAbsent(snapshot.getEventId(),
                ignored -> new ArrayList<>());
        LocalDateTime latest = values.stream().map(RadarEventSnapshot::getSnapshotAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(snapshot.getSnapshotAt());
        LocalDateTime keepAfter = (latest.isAfter(snapshot.getSnapshotAt()) ? latest : snapshot.getSnapshotAt())
                .minusDays(RadarCacheLayout.SNAPSHOT_KEEP_DAYS);
        values.removeIf(value -> value.getSnapshotAt() == null || value.getSnapshotAt().isBefore(keepAfter)
                || snapshot.getSnapshotAt().equals(value.getSnapshotAt()));
        if (!snapshot.getSnapshotAt().isBefore(keepAfter)) {
            values.add(snapshot);
        }
        return snapshot;
    }

    public Optional<RadarEventSnapshot> findLatestBefore(Long eventId, LocalDateTime before) {
        return latest(store.read().getSnapshots().getOrDefault(eventId, Collections.emptyList()), before);
    }

    public Map<String, RadarEventSnapshot> findLatestBeforeByEventKeys(Collection<String> eventKeys, LocalDateTime before) {
        if (eventKeys == null || eventKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        return store.query(state -> {
            RedisRadarCacheStore.prefetch(state.getEventIdsByKey(), eventKeys);
            List<Long> ids = eventKeys.stream().map(key -> state.getEventIdsByKey().get(key))
                    .filter(java.util.Objects::nonNull).toList();
            RedisRadarCacheStore.prefetch(state.getSnapshots(), ids);
            Map<String, RadarEventSnapshot> result = new LinkedHashMap<>();
            for (String key : eventKeys) {
                Long id = state.getEventIdsByKey().get(key);
                if (id != null) {
                    latest(state.getSnapshots().getOrDefault(id, Collections.emptyList()), before)
                            .ifPresent(value -> result.put(key, value));
                }
            }
            return result;
        });
    }

    private Optional<RadarEventSnapshot> latest(List<RadarEventSnapshot> snapshots, LocalDateTime before) {
        return snapshots.stream().filter(value -> value.getSnapshotAt() != null && value.getSnapshotAt().isBefore(before))
                .max(Comparator.comparing(RadarEventSnapshot::getSnapshotAt).thenComparing(RadarEventSnapshot::getId));
    }
}
