package com.finscope.dao.radar;

import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarSignal;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/** 新旧布局共用的过期口径，更新其他记录不会延长本条记录的有效期。 */
final class RadarCacheExpiry {
    private RadarCacheExpiry() {
    }

    static long deadline(String name, Object key, Object value, RadarCacheState state,
                         LocalDateTime now, int ttlHours, ZoneId zone) {
        LocalDateTime base = now;
        switch (name) {
            case "signals" -> {
                RadarSignal signal = (RadarSignal) value;
                base = signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt();
            }
            case "events" -> {
                RadarEvent event = (RadarEvent) value;
                base = event.getLastSeenAt() == null ? event.getFirstSeenAt() : event.getLastSeenAt();
            }
            case "signalIdsByItemId" -> {
                RadarSignal signal = state.getSignals().get(value);
                return signal == null ? 0L : deadline("signals", value, signal, state, now, ttlHours, zone);
            }
            case "eventIdsByKey" -> {
                return eventDeadline((Long) value, state, now, ttlHours, zone);
            }
            case "runs" -> base = ((RadarRefreshRun) value).getStartedAt();
            case "runSteps" -> {
                RadarRefreshRun run = state.getRuns().get(key);
                return run == null ? 0L : deadline("runs", key, run, state, now, ttlHours, zone);
            }
            case "pairDecisions" -> base = ((RadarPairDecision) value).getUpdatedAt();
            case "eventSignals", "snapshots", "evidence", "interpretations", "userStates",
                    "timelines", "timelineFingerprints", "researchLinks" -> {
                return eventDeadline((Long) key, state, now, ttlHours, zone);
            }
            case "notifications" -> {
                RadarEventWorkspace.Notification notification = (RadarEventWorkspace.Notification) value;
                return Math.min(expiration(notification.getCreatedAt(), ttlHours, zone),
                        eventDeadline(notification.getEventId(), state, now, ttlHours, zone));
            }
            case "agentRunsBySubject" -> {
                String subject = String.valueOf(key);
                if (subject.startsWith("RADAR_EVENT:")) {
                    return eventDeadline(Long.valueOf(subject.substring("RADAR_EVENT:".length())), state, now, ttlHours, zone);
                }
            }
            case "industryChainImpacts" -> {
                @SuppressWarnings("unchecked")
                Map<Long, IndustryChainEventImpact> impacts = (Map<Long, IndustryChainEventImpact>) value;
                RedisRadarCacheStore.prefetch(state.getEvents(), impacts.keySet());
                long latest = 0L;
                for (Long eventId : impacts.keySet()) {
                    latest = Math.max(latest, eventDeadline(eventId, state, now, ttlHours, zone));
                }
                return latest;
            }
            default -> {
                // 独立去重键从创建时计时，不随其他事件的写入无限续期。
            }
        }
        return expiration(base, ttlHours, zone);
    }

    private static long expiration(LocalDateTime base, int ttlHours, ZoneId zone) {
        return base == null ? 0L : base.plusHours(ttlHours).atZone(zone).toInstant().toEpochMilli();
    }

    private static long eventDeadline(Long id, RadarCacheState state, LocalDateTime now, int ttlHours, ZoneId zone) {
        if (id == null) {
            return 0L;
        }
        RadarEvent event = state.getEvents().get(id);
        return event == null ? 0L : deadline("events", id, event, state, now, ttlHours, zone);
    }
}
