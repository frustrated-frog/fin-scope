package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEvidence;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Repository
public class RadarEvidenceRepository {
    @Resource
    private RedisRadarCacheStore store;

    public void replaceForEvent(Long eventId, List<RadarEvidence> values) {
        store.update(state -> {
            List<RadarEvidence> stored = new ArrayList<RadarEvidence>();
            if (values != null) {
                for (RadarEvidence value : values) {
                    value.setEventId(eventId);
                    if (value.getCreatedAt() == null) {
                        value.setCreatedAt(LocalDateTime.now());
                    }
                    value.setId(store.stableId("evidence", eventId + ":" + value.getToolCode() + ":"
                            + value.getTitle() + ":" + value.getUrl()));
                    stored.add(value);
                }
            }
            state.getEvidence().put(eventId, stored);
            return null;
        });
    }

    public List<RadarEvidence> findByEventId(Long eventId) {
        List<RadarEvidence> values = new ArrayList<RadarEvidence>(store.read().getEvidence()
                .getOrDefault(eventId, java.util.Collections.emptyList()));
        values.sort(Comparator.comparing((RadarEvidence value) -> value.getPublishedAt() == null
                        ? value.getCreatedAt() : value.getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarEvidence::getId, Comparator.reverseOrder()));
        return values;
    }
}
