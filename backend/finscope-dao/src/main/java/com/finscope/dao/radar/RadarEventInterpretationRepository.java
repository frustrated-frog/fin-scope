package com.finscope.dao.radar;

import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.domain.radar.RadarEventInterpretation;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RadarEventInterpretationRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarEventInterpretation saveQueued(Long eventId, String fingerprint) {
        return store.update(state -> {
            List<RadarEventInterpretation> values = state.getInterpretations().computeIfAbsent(eventId,
                    ignored -> new ArrayList<RadarEventInterpretation>());
            for (RadarEventInterpretation existing : values) {
                if (fingerprint.equals(existing.getEventFingerprint())) {
                    return existing;
                }
            }
            RadarEventInterpretation value = new RadarEventInterpretation();
            value.setId(store.stableId("interpretation", eventId + ":" + fingerprint));
            value.setEventId(eventId);
            value.setEventFingerprint(fingerprint);
            value.setStatus("QUEUED");
            value.setCreatedAt(LocalDateTime.now());
            values.add(value);
            return value;
        });
    }

    public void update(RadarEventInterpretation value) {
        try {
            store.update(state -> {
                List<RadarEventInterpretation> values = state.getInterpretations().get(value.getEventId());
                if (values == null) {
                    throw new IllegalStateException("雷达事件解读不存在: " + value.getId());
                }
                for (int index = 0; index < values.size(); index++) {
                    if (value.getId().equals(values.get(index).getId())) {
                        values.set(index, value);
                        return null;
                    }
                }
                throw new IllegalStateException("雷达事件解读不存在: " + value.getId());
            });
        } catch (RuntimeException error) {
            throw new BusinessException(BizErrorCode.RADAR_INTERPRETATION_UPDATE_FAILED,
                    BizErrorCode.RADAR_INTERPRETATION_UPDATE_FAILED.format(value.getId()), error);
        }
    }

    public Optional<RadarEventInterpretation> findByEventFingerprint(Long eventId, String fingerprint) {
        return store.read().getInterpretations().getOrDefault(eventId, java.util.Collections.emptyList()).stream()
                .filter(value -> fingerprint.equals(value.getEventFingerprint())).findFirst();
    }

    public Optional<RadarEventInterpretation> findLatestByEventId(Long eventId) {
        return store.read().getInterpretations().getOrDefault(eventId, java.util.Collections.emptyList()).stream()
                .max(Comparator.comparing(RadarEventInterpretation::getId));
    }

    public List<RadarEventInterpretation> findHistory(Long eventId, int limit) {
        return store.read().getInterpretations().getOrDefault(eventId, java.util.Collections.emptyList()).stream()
                .sorted(Comparator.comparing(RadarEventInterpretation::getId).reversed())
                .limit(Math.max(1, Math.min(limit, 100))).collect(java.util.stream.Collectors.toList());
    }

    public Map<Long, RadarEventInterpretation> findLatestByEventIds(List<Long> eventIds) {
        Map<Long, RadarEventInterpretation> result = new LinkedHashMap<Long, RadarEventInterpretation>();
        if (eventIds == null) {
            return result;
        }
        for (Long eventId : eventIds) {
            findLatestByEventId(eventId).ifPresent(value -> result.put(eventId, value));
        }
        return result;
    }
}
