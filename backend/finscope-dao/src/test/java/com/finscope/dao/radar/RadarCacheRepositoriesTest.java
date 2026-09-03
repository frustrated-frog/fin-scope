package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarCacheRepositoriesTest {
    private final InMemoryRadarCacheStore store = new InMemoryRadarCacheStore();

    @BeforeEach
    void setUp() {
        store.state = new RadarCacheState();
    }

    @Test
    void storesSignalsEventsAndRelationsWithoutJdbc() {
        RadarRepository repository = inject(new RadarRepository());
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        RadarSignal signal = RadarSignal.builder().itemId("CLS:1").title("降息落地")
                .publishedAt(now).status("ACTIVE").build();

        RadarSignal storedSignal = repository.capture(signal, now);
        RadarEvent event = event("rate-cut", now);
        RadarEvent storedEvent = repository.saveEvent(event);
        RadarEventSignal link = new RadarEventSignal();
        link.setSignalId(storedSignal.getId());
        repository.replaceEventSignals(storedEvent.getId(), Collections.singletonList(link));

        assertEquals(storedSignal.getId(), repository.findSignalByItemId("CLS:1").orElseThrow().getId());
        assertEquals(storedEvent.getId(), repository.findEventByKey("rate-cut").orElseThrow().getId());
        assertEquals(1, repository.findSignalsByEventId(storedEvent.getId()).size());
        assertEquals(storedEvent.getId(), repository.findEventSignals(storedEvent.getId()).get(0).getEventId());
    }

    @Test
    void storesRunsSnapshotsEvidenceInterpretationsAndPairDecisionsInSharedState() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        RadarRefreshRunRepository runs = inject(new RadarRefreshRunRepository());
        RadarEventSnapshotRepository snapshots = inject(new RadarEventSnapshotRepository());
        RadarEvidenceRepository evidence = inject(new RadarEvidenceRepository());
        RadarEventInterpretationRepository interpretations = inject(new RadarEventInterpretationRepository());
        RadarPairDecisionRepository decisions = inject(new RadarPairDecisionRepository());

        RadarRefreshRun run = runs.startRun("run-1", "MANUAL", now);
        runs.startStep(run.getId(), "FETCH", now);
        runs.completeStep(run.getId(), "FETCH", "SUCCESS", 1, 1, "ok", now.plusMinutes(1));
        runs.completeRun(run.getId(), 1, 1, 1, null, now.plusMinutes(1));

        RadarEventSnapshot snapshot = new RadarEventSnapshot();
        snapshot.setEventId(7L);
        snapshot.setSnapshotAt(now);
        snapshots.save(snapshot);
        RadarEvidence item = new RadarEvidence();
        item.setTitle("证据");
        item.setCreatedAt(now);
        evidence.replaceForEvent(7L, Collections.singletonList(item));
        RadarEventInterpretation queued = interpretations.saveQueued(7L, "fp-1");
        queued.setStatus("SUCCESS");
        interpretations.update(queued);
        RadarPairDecision decision = new RadarPairDecision();
        decision.setPairKey("a:b");
        decision.setUpdatedAt(now);
        decisions.save(decision);

        assertEquals("SUCCESS", runs.findLatestCompletedRun().orElseThrow().getStatus());
        assertEquals(1, runs.findSteps(run.getId()).size());
        assertTrue(snapshots.findLatestBefore(7L, now.plusSeconds(1)).isPresent());
        assertEquals(1, evidence.findByEventId(7L).size());
        assertEquals("SUCCESS", interpretations.findLatestByEventId(7L).orElseThrow().getStatus());
        assertTrue(decisions.find("a:b").isPresent());
    }

    private RadarEvent event(String key, LocalDateTime now) {
        RadarEvent value = new RadarEvent();
        value.setEventKey(key);
        value.setCanonicalTitle("央行降息");
        value.setStatus("ACTIVE");
        value.setFirstSeenAt(now);
        value.setLastSeenAt(now);
        value.setUpdatedAt(now);
        return value;
    }

    private <T> T inject(T repository) {
        ReflectionTestUtils.setField(repository, "store", store);
        return repository;
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
