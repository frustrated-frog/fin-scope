package com.finscope.dao.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarEventWorkspaceRepositoryTest {
    private final InMemoryRadarCacheStore store = new InMemoryRadarCacheStore();
    private RadarEventWorkspaceRepository repository;

    @BeforeEach
    void setUp() {
        store.state = new RadarCacheState();
        repository = new RadarEventWorkspaceRepository();
        ReflectionTestUtils.setField(repository, "store", store);
    }

    @Test
    void storesTemporaryReadFollowAndDispositionState() {
        activeEvent(7L, "event-7");

        RadarEventWorkspace.State state = repository.updateState(7L, true, "LATER", true, "fp-1");
        RadarEventWorkspace.State unchanged = repository.updateState(7L, false, null, null, null);

        assertTrue(state.isRead());
        assertTrue(unchanged.isFollowed());
        assertEquals("LATER", unchanged.getDisposition());
        assertEquals("fp-1", unchanged.getLastViewedFingerprint());
        assertNotNull(unchanged.getReadAt());
    }

    @Test
    void listsOnlyActiveFollowedEventsAndBuildsCacheSummaries() {
        activeEvent(7L, "event-7");
        activeEvent(8L, "event-8");
        activeEvent(9L, "event-9").setStatus("EXPIRED");
        repository.updateState(7L, false, "ACTIVE", true, null);
        repository.updateState(8L, false, "ACTIVE", true, null);
        repository.updateState(9L, false, "ACTIVE", true, null);
        repository.createNotification(8L, "FOLLOWED_EVENT_CHANGED", "fp-8", "变化", "内容");

        Map<Long, RadarEventWorkspace.Summary> summaries = repository.findSummaries(Arrays.asList(7L, 8L));

        assertEquals(Arrays.asList(8L, 7L), repository.findFollowedEventIds(20));
        assertTrue(summaries.get(8L).isFollowed());
        assertEquals(1, summaries.get(8L).getUnreadNotificationCount());
        assertEquals(0, summaries.get(8L).getObservationCount());
    }

    @Test
    void storesTimelineResearchLinksAndDeduplicatedNotificationsInCache() {
        LocalDateTime now = LocalDateTime.now();
        activeEvent(7L, "event-7");

        repository.appendTimeline(7L, "fp", "READ", "查看", null, "STATE", 7L, now);
        repository.appendTimeline(7L, "fp", "READ", "查看", null, "STATE", 7L, now);
        RadarEventWorkspace.ResearchLink link = repository.linkResearchRun(7L, 22L, "为什么重要");
        assertTrue(repository.createNotification(7L, "CHANGE", "notice-fp", "变化", "内容"));
        assertFalse(repository.createNotification(7L, "CHANGE", "notice-fp", "变化", "内容"));

        assertEquals(1, repository.findTimeline(7L).size());
        assertEquals(link.getId(), repository.findResearchLinks(7L).get(0).getId());
        assertEquals(1, repository.findNotifications(10).size());
        assertEquals(1, repository.countNotificationsOn(LocalDate.now()));
        repository.markAllNotificationsRead();
        assertEquals(0, repository.countUnreadNotifications());
    }

    @Test
    void removesEphemeralObservationStorageAndValidatesDisposition() {
        assertEquals(Collections.emptyList(), repository.ensureDefaultObservation(7L, "观察公告"));
        assertEquals(Collections.emptyList(), repository.findObservations(7L));
        assertThrows(BusinessException.class,
                () -> repository.updateState(7L, false, "DELETED", false, null));
    }

    private RadarEvent activeEvent(Long id, String key) {
        RadarEvent event = new RadarEvent();
        event.setId(id);
        event.setEventKey(key);
        event.setStatus("ACTIVE");
        event.setFirstSeenAt(LocalDateTime.now());
        event.setLastSeenAt(LocalDateTime.now());
        store.state.getEvents().put(id, event);
        store.state.getEventIdsByKey().put(key, id);
        return event;
    }

    private static class InMemoryRadarCacheStore extends RedisRadarCacheStore {
        private RadarCacheState state = new RadarCacheState();

        @Override
        public synchronized RadarCacheState read() {
            return state;
        }

        @Override
        public synchronized <T> T update(Function<RadarCacheState, T> mutation) {
            return mutation.apply(state);
        }
    }
}
