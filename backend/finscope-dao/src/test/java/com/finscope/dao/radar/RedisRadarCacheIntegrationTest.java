package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接显式指定的隔离测试端口，绝不使用应用的 Redis 配置。 */
@EnabledIfSystemProperty(named = "finscope.test.redis-port", matches = "16389")
class RedisRadarCacheIntegrationTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private CountingStore store;
    private LocalDateTime now;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 16389));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        Set<String> keys = redis.keys("finscope:radar:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        store = newStore();
        now = LocalDateTime.now();
    }

    @Test
    void migratesChineseAcrossChunkBoundariesAndRemovesOrphansWithoutChangingIds() throws Exception {
        RadarCacheState legacy = new RadarCacheState();
        RadarEvent event = event(20L, "existing");
        legacy.getEvents().put(20L, event);
        legacy.getEventIdsByKey().put("existing", 20L);
        String body = "公告原文，中文边界。".repeat(30_000);
        RadarSignal signal = RadarSignal.builder().id(10L).itemId("news-1").content(body).publishedAt(now).build();
        legacy.getSignals().put(10L, signal);
        legacy.getSignalIdsByItemId().put("news-1", 10L);
        RadarEventSignal valid = link(20L, 10L);
        legacy.getEventSignals().put(20L, new ArrayList<>(List.of(valid, link(20L, 999L))));
        legacy.getEventSignals().put(99L, new ArrayList<>(List.of(valid)));
        RadarEventSnapshot old = new RadarEventSnapshot();
        old.setEventId(99L);
        old.setSnapshotAt(now);
        legacy.getSnapshots().put(99L, new ArrayList<>(List.of(old)));
        RadarEventWorkspace.Notification notification = new RadarEventWorkspace.Notification();
        notification.setId(7L);
        notification.setEventId(20L);
        notification.setCreatedAt(now);
        legacy.getNotifications().add(notification);
        legacy.setNextSequence(8L);
        String payload = mapper.writeValueAsString(legacy);
        redis.opsForValue().set(RadarCacheLayout.LEGACY_KEY, payload, Duration.ofHours(12));

        RadarCacheState migrated = store.read();

        assertEquals(20L, migrated.getEvents().get(20L).getId());
        assertEquals(10L, migrated.getSignalIdsByItemId().get("news-1"));
        assertEquals(body, migrated.getSignals().get(10L).getContent());
        assertEquals(1, migrated.getEventSignals().get(20L).size());
        assertFalse(migrated.getEventSignals().containsKey(99L));
        assertFalse(migrated.getSnapshots().containsKey(99L));
        assertFalse(migrated.getNotifications().get(0).isRead());
        assertEquals(8L, migrated.nextSequence());
        assertEquals(payload, redis.opsForValue().get(RadarCacheLayout.LEGACY_KEY));
        assertTrue(redis.getExpire(RadarCacheLayout.LEGACY_KEY) <= TimeUnit.HOURS.toSeconds(12));
    }

    @Test
    void statusReadAndUpdateNeverLoadEventsSignalsOrSnapshots() throws Exception {
        RadarCacheState legacy = new RadarCacheState();
        legacy.getEvents().put(20L, event(20L, "large-event"));
        RadarSignal signal = RadarSignal.builder().id(10L).itemId("big").content("正文".repeat(300_000)).publishedAt(now).build();
        legacy.getSignals().put(10L, signal);
        redis.opsForValue().set(RadarCacheLayout.LEGACY_KEY, mapper.writeValueAsString(legacy));
        store.read();
        store.readSections.clear();
        RadarRefreshRunRepository runs = repository(new RadarRefreshRunRepository(), store);
        RadarRefreshRun run = runs.startRun("small-status", "MANUAL", now);
        runs.startStep(run.getId(), "FETCH", now);
        runs.completeStep(run.getId(), "FETCH", "SUCCESS", 1, 1, "ok", now);
        runs.completeRun(run.getId(), 1, 1, 1, null, now);
        assertEquals("SUCCESS", runs.findLatestCompletedRun().orElseThrow().getStatus());
        assertTrue(store.readSections.stream().allMatch(name -> name.equals("runs") || name.equals("runSteps")));
        assertTrue(store.readBytes < 20_000L);
    }

    @Test
    void persistsNestedEditsWithoutRewritingUnrelatedRecordsOrRenewingTheirTtl() {
        RadarRepository radar = repository(new RadarRepository(), store);
        RadarEvent stored = radar.saveEvent(event(null, "first"));
        RadarEvent other = radar.saveEvent(event(null, "other"));
        String otherKey = key("events", other.getId());
        String original = redis.opsForValue().get(otherKey);
        Long before = redis.getExpire(otherKey, TimeUnit.MILLISECONDS);
        radar.updateDashboardCategory(stored.getId(), "COMPANY");
        assertEquals("COMPANY", radar.findEvent(stored.getId()).orElseThrow().getDashboardCategory());
        assertEquals(original, redis.opsForValue().get(otherKey));
        assertTrue(redis.getExpire(otherKey, TimeUnit.MILLISECONDS) <= before);
        assertTrue(redis.getExpire(otherKey, TimeUnit.MILLISECONDS) > before - 2000L);
    }

    @Test
    void refreshingEventPreservesWorkspaceAndAliasUntilTheNewEventDeadline() {
        RadarRepository radar = repository(new RadarRepository(), store);
        RadarEvent value = event(null, "extended");
        value.setFirstSeenAt(now.minusHours(35));
        value.setLastSeenAt(now.minusHours(35));
        RadarEvent saved = radar.saveEvent(value);
        repository(new RadarEventWorkspaceRepository(), store).updateState(saved.getId(), false, null, true, null);
        String stateKey = key("userStates", saved.getId());
        assertTrue(redis.getExpire(stateKey) < TimeUnit.HOURS.toSeconds(2));

        RadarEvent refreshed = event(null, "extended");
        radar.saveEvent(refreshed);

        assertTrue(redis.getExpire(stateKey) > TimeUnit.HOURS.toSeconds(35));
        assertTrue(store.read().getUserStates().get(saved.getId()).isFollowed());
        assertEquals(saved.getId(), radar.findEventByKey("extended").orElseThrow().getId());
        String prefix = RadarCacheLayout.prefix(redis.opsForValue().get(RadarCacheLayout.ACTIVE_KEY));
        RadarCacheLayout.Section alias = RadarCacheLayout.sections(mapper).stream()
                .filter(section -> section.name().equals("eventIdsByKey")).findFirst().orElseThrow();
        assertTrue(redis.getExpire(alias.key(prefix, "extended")) > TimeUnit.HOURS.toSeconds(35));
    }

    @Test
    void failedMigrationDoesNotPublishAPartialGenerationAndCanRetry() {
        redis.opsForValue().set(RadarCacheLayout.LEGACY_KEY, "{broken-json", Duration.ofHours(1));
        assertThrows(IllegalStateException.class, () -> store.read());
        assertNull(redis.opsForValue().get(RadarCacheLayout.ACTIVE_KEY));
        assertEquals("{broken-json", redis.opsForValue().get(RadarCacheLayout.LEGACY_KEY));
        redis.opsForValue().set(RadarCacheLayout.LEGACY_KEY, "{}", Duration.ofHours(1));
        assertTrue(store.read().getEvents().isEmpty());
        assertNotNull(redis.opsForValue().get(RadarCacheLayout.ACTIVE_KEY));
    }

    @Test
    void concurrentColdStartsPublishOneCompleteGeneration() throws Exception {
        RadarCacheState legacy = new RadarCacheState();
        legacy.getEvents().put(20L, event(20L, "cold-start"));
        redis.opsForValue().set(RadarCacheLayout.LEGACY_KEY, mapper.writeValueAsString(legacy));
        CountingStore other = newStore();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> store.read().getEvents().get(20L).getEventKey());
            var second = executor.submit(() -> other.read().getEvents().get(20L).getEventKey());
            assertEquals("cold-start", first.get(5, TimeUnit.SECONDS));
            assertEquals("cold-start", second.get(5, TimeUnit.SECONDS));
            assertEquals(1, store.read().getEvents().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentInstancesPreserveBothWorkspaceUpdates() throws Exception {
        Long eventId = repository(new RadarRepository(), store).saveEvent(event(null, "concurrent")).getId();
        CountingStore other = newStore();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentUpdate(store, eventId, true, entered, release));
            var second = executor.submit(() -> concurrentUpdate(other, eventId, false, entered, release));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            RadarEventWorkspace.State result = store.read().getUserStates().get(eventId);
            assertTrue(result.isRead());
            assertTrue(result.isFollowed());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsLateChildWriteWhenParentHasExpired() {
        Long eventId = repository(new RadarRepository(), store).saveEvent(event(null, "expired-parent")).getId();
        redis.delete(key("events", eventId));
        RadarEventSnapshot snapshot = new RadarEventSnapshot();
        snapshot.setEventId(eventId);
        snapshot.setSnapshotAt(now);
        assertThrows(IllegalStateException.class, () -> repository(new RadarEventSnapshotRepository(), store).save(snapshot));
        assertNull(redis.opsForValue().get(key("snapshots", eventId)));
    }

    @Test
    void multiRecordQueryRetriesIfAnotherWriterCommitsInTheMiddle() {
        Long id = repository(new RadarRepository(), store).saveEvent(event(null, "query" )).getId();
        CountingStore other = newStore();
        AtomicInteger attempts = new AtomicInteger();
        String title = store.query(state -> {
            String read = state.getEvents().get(id).getCanonicalTitle();
            if (attempts.getAndIncrement() == 0) {
                other.update(next -> {
                    next.getEvents().get(id).setCanonicalTitle("更新后的标题");
                    return null;
                });
            }
            return read;
        });
        assertEquals("更新后的标题", title);
        assertEquals(2, attempts.get());
    }

    @Test
    void noChangeMutationAlsoRetriesAConcurrentVersionChange() {
        Long id = repository(new RadarRepository(), store).saveEvent(event(null, "read-in-update")).getId();
        CountingStore other = newStore();
        AtomicInteger attempts = new AtomicInteger();
        String title = store.update(state -> {
            String read = state.getEvents().get(id).getCanonicalTitle();
            if (attempts.getAndIncrement() == 0) {
                other.update(next -> {
                    next.getEvents().get(id).setCanonicalTitle("新版本");
                    return null;
                });
            }
            return read;
        });
        assertEquals("新版本", title);
        assertEquals(2, attempts.get());
    }

    @Test
    void retryingEventSavePreservesEvidenceFromTheConcurrentWriter() {
        RadarRepository radar = repository(new RadarRepository(), store);
        RadarEvent initial = event(null, "retry-evidence");
        initial.setEvidenceStatus("OLD");
        RadarEvent saved = radar.saveEvent(initial);
        CountingStore other = newStore();
        StringRedisTemplate observed = org.mockito.Mockito.spy(redis);
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                other.update(state -> {
                    state.getEvents().get(saved.getId()).setEvidenceStatus("LATEST");
                    return null;
                });
            }
            return invocation.callRealMethod();
        }).when(observed).execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        ReflectionTestUtils.setField(store, "redisTemplate", observed);
        RadarEvent incoming = event(null, "retry-evidence");
        incoming.setSummary("新的生产摘要");

        radar.saveEvent(incoming);

        RadarEvent result = radar.findEvent(saved.getId()).orElseThrow();
        assertEquals("LATEST", result.getEvidenceStatus());
        assertEquals("新的生产摘要", result.getSummary());
    }

    @Test
    void validatesAllRedisTypesBeforeApplyingAnyRecordChanges() {
        RadarEvent initial = repository(new RadarRepository(), store).saveEvent(event(null, "atomic"));
        assertNotNull(initial);
        String generation = redis.opsForValue().get(RadarCacheLayout.ACTIVE_KEY);
        String prefix = RadarCacheLayout.prefix(generation);
        redis.opsForValue().set(prefix + "userStates:index", "wrong-type");
        String original = redis.opsForValue().get(key("events", initial.getId()));
        assertThrows(RuntimeException.class, () -> store.update(state -> {
            state.getEvents().get(initial.getId()).setCanonicalTitle("不应该提交");
            RadarEventWorkspace.State workspace = new RadarEventWorkspace.State();
            workspace.setEventId(initial.getId());
            state.getUserStates().put(initial.getId(), workspace);
            return null;
        }));
        assertEquals(original, redis.opsForValue().get(key("events", initial.getId())));
        assertNull(redis.opsForValue().get(key("userStates", initial.getId())));
    }

    @Test
    void prefetchesManyInterpretationsAndParentsInBoundedBatches() {
        RadarRepository radar = repository(new RadarRepository(), store);
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            ids.add(radar.saveEvent(event(null, "event-" + index)).getId());
        }
        store.readSections.clear();
        store.batchSizes.clear();
        repository(new RadarEventInterpretationRepository(), store).findLatestByEventIds(ids);
        assertTrue(store.batchSizes.stream().allMatch(size -> size <= 100));
        assertTrue(store.readSections.size() <= 4);
    }

    @Test
    void batchEventWriteHasSignalRelationsAndSnapshotBatchRemainsBounded() {
        RadarRepository radar = repository(new RadarRepository(), store);
        RadarSignal signal = radar.capture(RadarSignal.builder().itemId("batch-signal").publishedAt(now).build(), now);
        List<RadarEvent> events = new ArrayList<>();
        Map<String, List<RadarEventSignal>> links = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 101; index++) {
            RadarEvent value = event(null, "batch-" + index);
            events.add(value);
            links.put(value.getEventKey(), List.of(link(null, signal.getId())));
        }
        List<RadarEvent> saved = radar.saveEventsWithSignals(events, links);
        assertEquals(101, saved.size());
        assertEquals(signal.getId(), radar.findDetailSnapshot(saved.get(100).getId()).orElseThrow().getSignals().get(0).getId());
        List<RadarEventSnapshot> snapshots = new ArrayList<>();
        for (RadarEvent value : saved) {
            RadarEventSnapshot snapshot = new RadarEventSnapshot();
            snapshot.setEventId(value.getId());
            snapshot.setSnapshotAt(now);
            snapshots.add(snapshot);
        }
        RadarEventSnapshotRepository history = repository(new RadarEventSnapshotRepository(), store);
        history.saveBatch(snapshots);
        store.readSections.clear();
        Map<String, RadarEventSnapshot> latest = history.findLatestBeforeByEventKeys(
                events.stream().map(RadarEvent::getEventKey).toList(), now.plusSeconds(1));
        assertEquals(101, latest.size());
        assertTrue(store.readSections.stream().allMatch(name -> List.of("events", "eventIdsByKey", "snapshots").contains(name)));
    }

    private void concurrentUpdate(CountingStore target, Long id, boolean markRead,
                                  CountDownLatch entered, CountDownLatch release) {
        target.update(state -> {
            RadarEventWorkspace.State value = state.getUserStates().get(id);
            if (value == null) {
                value = new RadarEventWorkspace.State();
                value.setEventId(id);
            }
            entered.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发测试超时");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            if (markRead) {
                value.setRead(true);
            } else {
                value.setFollowed(true);
            }
            state.getUserStates().put(id, value);
            return null;
        });
    }

    private RadarEvent event(Long id, String eventKey) {
        RadarEvent value = new RadarEvent();
        value.setId(id);
        value.setEventKey(eventKey);
        value.setCanonicalTitle("事件 " + eventKey);
        value.setFirstSeenAt(now);
        value.setLastSeenAt(now);
        value.setUpdatedAt(now);
        value.setStatus("ACTIVE");
        return value;
    }

    private RadarEventSignal link(Long eventId, Long signalId) {
        RadarEventSignal link = new RadarEventSignal();
        link.setEventId(eventId);
        link.setSignalId(signalId);
        return link;
    }

    private CountingStore newStore() {
        CountingStore result = new CountingStore();
        ReflectionTestUtils.setField(result, "redisTemplate", redis);
        ReflectionTestUtils.setField(result, "objectMapper", mapper);
        ReflectionTestUtils.setField(result, "properties", new EphemeralContentCacheProperties());
        ReflectionTestUtils.setField(result, "clock", Clock.systemDefaultZone());
        return result;
    }

    private <T> T repository(T value, RedisRadarCacheStore target) {
        ReflectionTestUtils.setField(value, "store", target);
        return value;
    }

    private String key(String collection, Object id) {
        String generation = redis.opsForValue().get(RadarCacheLayout.ACTIVE_KEY);
        RadarCacheLayout.Section section = RadarCacheLayout.sections(mapper).stream()
                .filter(value -> value.name().equals(collection)).findFirst().orElseThrow();
        return section.key(RadarCacheLayout.prefix(generation), id);
    }

    private static class CountingStore extends RedisRadarCacheStore {
        private final List<String> readSections = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();
        private long readBytes;

        @Override
        List<String> readRecords(String generation, RadarCacheLayout.Section section, Collection<?> ids) {
            readSections.add(section.name());
            batchSizes.add(ids.size());
            List<String> result = super.readRecords(generation, section, ids);
            readBytes += result.stream().filter(java.util.Objects::nonNull)
                    .mapToLong(value -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
            return result;
        }
    }
}
