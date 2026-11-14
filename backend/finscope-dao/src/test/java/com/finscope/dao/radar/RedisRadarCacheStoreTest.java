package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarRefreshRun;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRadarCacheStoreTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    private RedisRadarCacheStore store;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        store = new RedisRadarCacheStore();
        ReflectionTestUtils.setField(store, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(store, "objectMapper", mapper);
        ReflectionTestUtils.setField(store, "properties", new EphemeralContentCacheProperties());
        ReflectionTestUtils.setField(store, "clock", clock);
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
        lenient().when(values.get(RadarCacheLayout.ACTIVE_KEY)).thenReturn("test");
        lenient().when(values.get(RadarCacheLayout.prefix("test") + "revision")).thenReturn("0");
    }

    @Test
    void readingSingleEventDoesNotReadLegacyStateOrOtherCollections() throws Exception {
        RadarEvent event = new RadarEvent();
        event.setId(42L);
        event.setLastSeenAt(LocalDateTime.now(clock));
        when(values.multiGet(anyCollection())).thenReturn(List.of(mapper.writeValueAsString(event)));

        assertEquals(42L, store.read().getEvents().get(42L).getId());

        verify(values).multiGet(List.of("finscope:radar:v2:test:events:NDI"));
        verify(values, never()).get(RadarCacheLayout.LEGACY_KEY);
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    void commitsOnlyChangedRunWithAbsoluteBusinessExpiration() {
        when(values.multiGet(anyCollection())).thenReturn(Collections.singletonList(null));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any()))
                .thenReturn(1L);
        LocalDateTime now = LocalDateTime.now(clock);
        store.update(state -> {
            RadarRefreshRun run = new RadarRefreshRun();
            run.setId(7L);
            run.setStartedAt(now);
            run.setStatus("RUNNING");
            state.getRuns().put(7L, run);
            return null;
        });

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.RedisScript.class), keys.capture(),
                eq("0"), eq(String.valueOf(clock.millis())), eq("Nw"), contains("RUNNING"),
                eq(String.valueOf(clock.millis() + TimeUnit.HOURS.toMillis(36))), eq("0"));
        assertEquals(4, keys.getValue().size());
        assertTrue(keys.getValue().get(1).contains(":runs:"));
        verify(values, never()).set(eq(RadarCacheLayout.LEGACY_KEY), anyString());
    }

    @Test
    void readsCommittedDataWhileWriterIsStillMutating() throws Exception {
        when(values.multiGet(anyCollection())).thenReturn(Collections.singletonList(null));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any()))
                .thenReturn(1L);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var writer = executor.submit(() -> store.update(state -> {
                RadarRefreshRun run = new RadarRefreshRun();
                run.setId(1L);
                run.setStartedAt(LocalDateTime.now(clock));
                state.getRuns().put(1L, run);
                entered.countDown();
                try {
                    assertTrue(release.await(3, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return null;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertNull(executor.submit(() -> store.read().getRuns().get(1L)).get(1, TimeUnit.SECONDS));
            release.countDown();
            writer.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void prunesAlreadyOrphanedChildrenAndExpiredSignalLinks() {
        LocalDateTime now = LocalDateTime.now(clock);
        RadarCacheState state = new RadarCacheState();
        RadarEvent live = new RadarEvent();
        live.setId(20L);
        live.setLastSeenAt(now);
        state.getEvents().put(20L, live);
        RadarSignal old = RadarSignal.builder().id(10L).firstSeenAt(now.minusHours(37)).build();
        state.getSignals().put(10L, old);
        RadarEventSignal missing = new RadarEventSignal();
        missing.setSignalId(10L);
        state.getEventSignals().put(20L, new ArrayList<>(List.of(missing)));
        state.getEventSignals().put(99L, new ArrayList<>(List.of(missing)));
        state.getSnapshots().put(99L, new ArrayList<>());

        RadarCachePruner.prune(state, now, 36, ZoneOffset.UTC);

        assertTrue(state.getSignals().isEmpty());
        assertTrue(state.getEventSignals().isEmpty());
        assertTrue(state.getSnapshots().isEmpty());
        assertEquals(1, state.getEvents().size());
    }

    @Test
    void keepsStablePositiveIdsAndRemainingTtl() {
        assertEquals(store.stableId("event", "same"), store.stableId("event", "same"));
        assertTrue(store.stableId("event", "same") > 0L);
        assertThrows(IllegalArgumentException.class, () -> store.stableId("", "same"));
        assertEquals(35L, store.remainingTtl(LocalDateTime.now(clock).minusHours(1), LocalDateTime.now(clock)).toHours());
        assertTrue(store.remainingTtl(LocalDateTime.now(clock).minusHours(37), LocalDateTime.now(clock)).isZero());
    }
}
