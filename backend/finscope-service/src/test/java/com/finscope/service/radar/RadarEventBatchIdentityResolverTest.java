package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RadarEventBatchIdentityResolverTest {
    private static final String LEGACY_KEY = "英伟达:事件:信息:20260805";
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void assignsCompetingLegacyIdentityToStrongerClusterRegardlessOfInputOrder() {
        RadarRepository repository = repositoryWithLegacyEvent();

        ResolutionFixture firstOrder = resolve(repository, false);
        ResolutionFixture reversedOrder = resolve(repository, true);

        assertEquals(LEGACY_KEY, firstOrder.strong.getEvent().getEventKey());
        assertEquals("英伟达:事件:信息:20260805:5%", firstOrder.weak.getEvent().getEventKey());
        assertEquals(LEGACY_KEY, reversedOrder.strong.getEvent().getEventKey());
        assertEquals("英伟达:事件:信息:20260805:5%", reversedOrder.weak.getEvent().getEventKey());
        assertEquals("nativeKeys=2,legacyReused=1,legacyConflicts=1,fallbackKept=1",
                firstOrder.resolution.summary());
        assertEquals(firstOrder.resolution.summary(), reversedOrder.resolution.summary());
    }

    @Test
    void rejectsDuplicateNativeIdentitiesBeforeLegacyResolution() {
        RadarEventBatchIdentityResolver resolver = resolver(mock(RadarRepository.class));
        RadarClusteringService.ClusterResult first = cluster("英伟达:事件:信息:20260805:5%", 1, 1);
        RadarClusteringService.ClusterResult second = cluster("英伟达:事件:信息:20260805:5%", 1, 2);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(Arrays.asList(first, second)));

        assertEquals("聚类合并后仍出现重复原生事件身份: 英伟达:事件:信息:20260805:5%", error.getMessage());
    }

    private ResolutionFixture resolve(RadarRepository repository, boolean reversed) {
        RadarEventBatchIdentityResolver resolver = resolver(repository);
        RadarClusteringService.ClusterResult strong = cluster(
                "英伟达:事件:信息:20260805:10%", 2, 1);
        RadarClusteringService.ClusterResult weak = cluster(
                "英伟达:事件:信息:20260805:5%", 1, 3);
        List<RadarClusteringService.ClusterResult> clusters = reversed
                ? Arrays.asList(weak, strong) : Arrays.asList(strong, weak);
        RadarEventIdentityResolution resolution = resolver.resolve(clusters);
        return new ResolutionFixture(strong, weak, resolution);
    }

    private RadarRepository repositoryWithLegacyEvent() {
        RadarRepository repository = mock(RadarRepository.class);
        RadarEvent legacy = new RadarEvent();
        legacy.setEventKey(LEGACY_KEY);
        legacy.setLastSeenAt(now.minusMinutes(30));
        when(repository.findEventByKey(LEGACY_KEY)).thenReturn(Optional.of(legacy));
        return repository;
    }

    private RadarEventBatchIdentityResolver resolver(RadarRepository repository) {
        RadarEventBatchIdentityResolver resolver = new RadarEventBatchIdentityResolver();
        ReflectionTestUtils.setField(resolver, "repository", repository);
        return resolver;
    }

    private RadarClusteringService.ClusterResult cluster(String eventKey, int signalCount, long firstSignalId) {
        RadarSignal representative = signal(firstSignalId);
        RadarClusteringService.ClusterResult cluster = new RadarClusteringService.ClusterResult(representative);
        for (int index = 1; index < signalCount; index++) {
            cluster.getSignals().add(signal(firstSignalId + index));
        }
        cluster.getEvent().setEventKey(eventKey);
        cluster.getEvent().setSourceCount(signalCount);
        cluster.getEvent().setSignalCount(signalCount);
        cluster.getEvent().setCategoryCode("COMPANY");
        cluster.getEvent().setLastSeenAt(now.minusMinutes(firstSignalId));
        return cluster;
    }

    private RadarSignal signal(long id) {
        RadarSignal signal = new RadarSignal();
        signal.setId(id);
        return signal;
    }

    private static final class ResolutionFixture {
        private final RadarClusteringService.ClusterResult strong;
        private final RadarClusteringService.ClusterResult weak;
        private final RadarEventIdentityResolution resolution;

        private ResolutionFixture(RadarClusteringService.ClusterResult strong,
                                  RadarClusteringService.ClusterResult weak,
                                  RadarEventIdentityResolution resolution) {
            this.strong = strong;
            this.weak = weak;
            this.resolution = resolution;
        }
    }
}
