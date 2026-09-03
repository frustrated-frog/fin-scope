package com.finscope.dao.radar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisRadarCacheStore {
    static final String STATE_KEY = "finscope:radar:state";

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private EphemeralContentCacheProperties properties;
    private Clock clock = Clock.systemDefaultZone();

    public synchronized RadarCacheState read() {
        return readAt(LocalDateTime.now(clock));
    }

    public synchronized <T> T update(Function<RadarCacheState, T> mutation) {
        LocalDateTime now = LocalDateTime.now(clock);
        RadarCacheState state = readAt(now);
        T result = mutation.apply(state);
        prune(state, now);
        write(state);
        return result;
    }

    public long stableId(String namespace, String businessKey) {
        if (namespace == null || namespace.trim().isEmpty() || businessKey == null || businessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("缓存标识的命名空间和业务键不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (namespace.trim() + ':' + businessKey.trim()).getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                value = (value << 8) | (digest[index] & 0xffL);
            }
            value &= Long.MAX_VALUE;
            return value == 0L ? 1L : value;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", error);
        }
    }

    public Duration remainingTtl(LocalDateTime baseTime, LocalDateTime now) {
        if (baseTime == null || now == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(now, baseTime.plusHours(normalizedTtlHours()));
        return remaining.isNegative() || remaining.isZero() ? Duration.ZERO : remaining;
    }

    private RadarCacheState readAt(LocalDateTime now) {
        try {
            String payload = redisTemplate.opsForValue().get(STATE_KEY);
            RadarCacheState state = payload == null || payload.trim().isEmpty()
                    ? new RadarCacheState() : objectMapper.readerFor(RadarCacheState.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
            normalize(state);
            prune(state, now);
            return state;
        } catch (RuntimeException | JsonProcessingException error) {
            throw new IllegalStateException("Redis 雷达临时缓存读取失败", error);
        }
    }

    private void write(RadarCacheState state) {
        try {
            String payload = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(STATE_KEY, payload,
                    TimeUnit.HOURS.toMillis(normalizedTtlHours()), TimeUnit.MILLISECONDS);
        } catch (RuntimeException | JsonProcessingException error) {
            throw new IllegalStateException("Redis 雷达临时缓存写入失败", error);
        }
    }

    private void prune(RadarCacheState state, LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(normalizedTtlHours());
        Set<Long> expiredSignalIds = new HashSet<Long>();
        Iterator<Map.Entry<Long, RadarSignal>> signals = state.getSignals().entrySet().iterator();
        while (signals.hasNext()) {
            Map.Entry<Long, RadarSignal> entry = signals.next();
            RadarSignal signal = entry.getValue();
            LocalDateTime baseTime = signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt();
            if (baseTime == null || baseTime.isBefore(cutoff)) {
                expiredSignalIds.add(entry.getKey());
                signals.remove();
            }
        }
        state.getSignalIdsByItemId().entrySet().removeIf(entry -> expiredSignalIds.contains(entry.getValue()));

        Set<Long> expiredEventIds = new HashSet<Long>();
        Iterator<Map.Entry<Long, RadarEvent>> events = state.getEvents().entrySet().iterator();
        while (events.hasNext()) {
            Map.Entry<Long, RadarEvent> entry = events.next();
            RadarEvent event = entry.getValue();
            LocalDateTime baseTime = event.getLastSeenAt() == null ? event.getFirstSeenAt() : event.getLastSeenAt();
            if (baseTime == null || baseTime.isBefore(cutoff)) {
                expiredEventIds.add(entry.getKey());
                events.remove();
            }
        }
        state.getEventIdsByKey().entrySet().removeIf(entry -> expiredEventIds.contains(entry.getValue()));
        for (Long eventId : expiredEventIds) {
            state.getEventSignals().remove(eventId);
            state.getSnapshots().remove(eventId);
            state.getEvidence().remove(eventId);
            state.getInterpretations().remove(eventId);
            state.getUserStates().remove(eventId);
            state.getTimelines().remove(eventId);
            state.getTimelineFingerprints().remove(eventId);
            state.getResearchLinks().remove(eventId);
        }
        state.getNotifications().removeIf(value -> value.getEventId() != null && expiredEventIds.contains(value.getEventId()));
        for (Long eventId : expiredEventIds) {
            state.getAgentRunsBySubject().remove("RADAR_EVENT:" + eventId);
        }
        state.getIndustryChainImpacts().values().forEach(values -> {
            for (Long eventId : expiredEventIds) {
                values.remove(eventId);
            }
        });
        state.getIndustryChainImpacts().entrySet().removeIf(entry -> entry.getValue().isEmpty());
        state.getAgentRunsBySubject().values().forEach(values -> values.removeIf(value ->
                value.getCreatedAt() == null || value.getCreatedAt().isBefore(cutoff)));
        state.getAgentRunsBySubject().entrySet().removeIf(entry -> entry.getValue().isEmpty());
        state.getPairDecisions().entrySet().removeIf(entry -> entry.getValue().getUpdatedAt() != null
                && entry.getValue().getUpdatedAt().isBefore(cutoff));
        Set<Long> expiredRunIds = new HashSet<Long>();
        state.getRuns().entrySet().removeIf(entry -> {
            LocalDateTime startedAt = entry.getValue().getStartedAt();
            boolean expired = startedAt == null || startedAt.isBefore(cutoff);
            if (expired) {
                expiredRunIds.add(entry.getKey());
            }
            return expired;
        });
        for (Long runId : expiredRunIds) {
            state.getRunSteps().remove(runId);
        }
    }

    private void normalize(RadarCacheState state) {
        if (state.getSignals() == null || state.getSignalIdsByItemId() == null || state.getEvents() == null
                || state.getEventIdsByKey() == null || state.getEventSignals() == null || state.getSnapshots() == null
                || state.getEvidence() == null || state.getInterpretations() == null || state.getPairDecisions() == null
                || state.getRuns() == null || state.getRunSteps() == null || state.getUserStates() == null
                || state.getTimelines() == null || state.getTimelineFingerprints() == null
                || state.getResearchLinks() == null || state.getNotifications() == null
                || state.getNotificationFingerprints() == null || state.getAgentRunsBySubject() == null
                || state.getIndustryChainImpacts() == null) {
            throw new IllegalStateException("Redis 雷达临时缓存结构不完整");
        }
    }

    private int normalizedTtlHours() {
        return Math.max(1, properties.getTtlHours());
    }
}
