package com.finscope.dao.industrychain;

import com.finscope.dao.radar.RadarCacheState;
import com.finscope.dao.radar.RedisRadarCacheStore;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryChainEventImpactRepositoryTest {
    private final InMemoryRadarCacheStore store = new InMemoryRadarCacheStore();
    private IndustryChainEventImpactRepository repository;

    @BeforeEach
    void setUp() {
        store.state = new RadarCacheState();
        repository = new IndustryChainEventImpactRepository();
        ReflectionTestUtils.setField(repository, "store", store);
    }

    @Test
    void upsertsOneTemporaryRelationshipAndPreservesPathOrder() {
        IndustryChainEventImpact first = impact(7L, "product:hbm",
                Arrays.asList("product:hbm", "product:server"));
        IndustryChainEventImpact updated = impact(7L, "product:hbm",
                Arrays.asList("product:hbm", "product:server", "stage:application"));

        assertTrue(repository.upsert(first, LocalDateTime.of(2026, 8, 11, 10, 0)));
        assertFalse(repository.upsert(updated, LocalDateTime.of(2026, 8, 11, 11, 0)));

        List<IndustryChainEventImpact> restored = repository.findByChainId(1L);
        assertEquals(1, restored.size());
        assertEquals(Arrays.asList("product:hbm", "product:server", "stage:application"),
                restored.get(0).getPathNodeKeys());
        Map<Long, String> versions = repository.findAnalysisVersionsByRadarEventId(1L);
        assertEquals("RULES_V1", versions.get(7L));
    }

    private IndustryChainEventImpact impact(Long eventId, String directNodeKey, List<String> path) {
        IndustryChainEventImpact impact = new IndustryChainEventImpact();
        impact.setChainId(1L);
        impact.setRadarEventId(eventId);
        impact.setDirectNodeKey(directNodeKey);
        impact.setDirection(IndustryChainEventImpact.Direction.POSITIVE);
        impact.setMechanism(IndustryChainEventImpact.Mechanism.PRICE);
        impact.setHorizon(IndustryChainEventImpact.Horizon.SHORT);
        impact.setConfidence(IndustryChainEventImpact.Confidence.HIGH);
        impact.setImpactSummary("价格上涨沿服务器链路传导");
        impact.setAnalysisVersion("RULES_V1");
        impact.setPathNodeKeys(path);
        return impact;
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
