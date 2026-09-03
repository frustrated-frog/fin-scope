package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRadarCacheStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRadarCacheStore store;

    @BeforeEach
    void setUp() {
        EphemeralContentCacheProperties properties = new EphemeralContentCacheProperties();
        properties.setTtlHours(36);
        store = new RedisRadarCacheStore();
        ReflectionTestUtils.setField(store, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(store, "objectMapper", new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(store, "properties", properties);
        ReflectionTestUtils.setField(store, "clock", Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void writesRadarStateWithConfiguredThirtySixHourTtlAndStableIds() {
        when(valueOperations.get(RedisRadarCacheStore.STATE_KEY)).thenReturn(null);

        long first = store.stableId("event", "central-bank-rate-cut");
        long second = store.stableId("event", "central-bank-rate-cut");
        store.update(state -> {
            state.getEventIdsByKey().put("central-bank-rate-cut", first);
            return null;
        });

        assertEquals(first, second);
        assertTrue(first > 0);
        verify(valueOperations).set(eq(RedisRadarCacheStore.STATE_KEY), startsWith("{"),
                eq(TimeUnit.HOURS.toMillis(36)), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void prunesExpiredSignalsEventsAndOwnedRelationsBeforeMutation() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        RadarSignal oldSignal = RadarSignal.builder().id(10L).itemId("old-news")
                .firstSeenAt(now.minusHours(37)).lastSeenAt(now.minusMinutes(1)).build();
        RadarEvent oldEvent = new RadarEvent();
        oldEvent.setId(20L);
        oldEvent.setEventKey("old-event");
        oldEvent.setLastSeenAt(now.minusHours(37));
        RadarEventSignal link = new RadarEventSignal();
        link.setEventId(20L);
        link.setSignalId(10L);

        RadarCacheState cached = new RadarCacheState();
        cached.getSignals().put(10L, oldSignal);
        cached.getSignalIdsByItemId().put("old-news", 10L);
        cached.getEvents().put(20L, oldEvent);
        cached.getEventIdsByKey().put("old-event", 20L);
        cached.getEventSignals().put(20L, Collections.singletonList(link));
        when(valueOperations.get(RedisRadarCacheStore.STATE_KEY)).thenReturn(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(cached));

        RadarCacheState result = store.update(state -> state);

        assertFalse(result.getSignals().containsKey(10L));
        assertFalse(result.getSignalIdsByItemId().containsKey("old-news"));
        assertFalse(result.getEvents().containsKey(20L));
        assertFalse(result.getEventIdsByKey().containsKey("old-event"));
        assertFalse(result.getEventSignals().containsKey(20L));
    }

    @Test
    void readsWorkspaceNotificationsWithDerivedReadProperty() throws Exception {
        RadarEventWorkspace.Notification notification = new RadarEventWorkspace.Notification();
        notification.setId(1L);
        notification.setEventId(2L);
        notification.setCreatedAt(LocalDateTime.of(2026, 9, 4, 11, 0));
        RadarCacheState cached = new RadarCacheState();
        cached.getNotifications().add(notification);
        when(valueOperations.get(RedisRadarCacheStore.STATE_KEY)).thenReturn(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(cached));

        RadarCacheState result = store.read();

        assertEquals(1, result.getNotifications().size());
        assertFalse(result.getNotifications().get(0).isRead());
    }
}
