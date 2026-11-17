package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RadarEventSnapshotRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarEventSnapshot save(RadarEventSnapshot snapshot) {
        saveBatch(List.of(snapshot));
        return snapshot;
    }

    public void saveBatch(List<RadarEventSnapshot> snapshots) {
        if (!snapshots.isEmpty()) {
            store.appendSnapshots(snapshots);
        }
    }

    public Optional<RadarEventSnapshot> findLatestBefore(Long eventId, LocalDateTime before) {
        return Optional.ofNullable(store.latestSnapshots(List.of(eventId), before).get(eventId));
    }

    public Map<String, RadarEventSnapshot> findLatestBeforeByEventKeys(Collection<String> eventKeys, LocalDateTime before) {
        if (eventKeys == null || eventKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        return store.query(state -> {
            RedisRadarCacheStore.prefetch(state.getEventIdsByKey(), eventKeys);
            List<Long> ids = eventKeys.stream().map(key -> state.getEventIdsByKey().get(key))
                    .filter(java.util.Objects::nonNull).toList();
            Map<Long, RadarEventSnapshot> latest = store.latestSnapshots(ids, before);
            Map<String, RadarEventSnapshot> result = new LinkedHashMap<>();
            for (String key : eventKeys) {
                Long id = state.getEventIdsByKey().get(key);
                if (id != null) {
                    RadarEventSnapshot snapshot = latest.get(id);
                    if (snapshot != null) {
                        result.put(key, snapshot);
                    }
                }
            }
            return result;
        });
    }

}
