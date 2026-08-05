package com.finscope.service.research.material;

import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialCacheEntry;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.dao.cache.ResearchMaterialCacheRepository;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.research.material.ResearchMaterialProvider;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.marketdata.ProviderRoutePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchMaterialGatewayTest {
    @Test
    void preservesSuccessfulMaterialsWhenAnotherProviderFails() {
        ResearchMaterialProvider failed = provider("FAILED", 10, true);
        ResearchMaterialProvider healthy = provider("HEALTHY", 20, false);
        ProviderRequestGuard guard = new ProviderRequestGuard();
        ResearchMaterialGateway gateway = new ResearchMaterialGateway(
                Arrays.asList(failed, healthy), new ProviderRoutePolicy(guard), guard);

        ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "订单", 10));

        assertEquals(1, result.getMaterials().size());
        assertEquals("HEALTHY", result.getMaterials().get(0).getProviderCode());
        assertTrue(result.getWarnings().get(0).contains("FAILED"));
    }

    @Test
    void reportsWhenSupportedProvidersAreFilteredByRoutingHealth() {
        ResearchMaterialProvider provider = provider("OPEN_CIRCUIT", 10, false);
        ProviderRequestGuard guard = new ProviderRequestGuard();
        ProviderRoutePolicy routes = mock(ProviderRoutePolicy.class);
        when(routes.orderExternal(anyList(), anyString(), any())).thenReturn(Collections.emptyList());
        ResearchMaterialGateway gateway = new ResearchMaterialGateway(
                Collections.singletonList(provider), routes, guard);

        ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "订单", 10));

        assertTrue(result.getMaterials().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("熔断或暂停"));
    }

    @Test
    void returnsCachedMaterialsWithoutCallingProviders() {
        FakeCache cache = new FakeCache(new ResearchMaterialCacheEntry(
                Collections.singletonList(material("cached", "缓存资讯")),
                Collections.singletonList("cached-warning"), LocalDateTime.now()));
        ResearchMaterialProvider provider = provider("NETWORK", 10, true);
        ProviderRequestGuard guard = new ProviderRequestGuard();
        ResearchMaterialGateway gateway = new ResearchMaterialGateway(
                Collections.singletonList(provider), new ProviderRoutePolicy(guard), guard, cache);

        ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "订单", 10));

        assertEquals("cached", result.getMaterials().get(0).getExternalId());
        assertEquals(1, cache.reads);
        assertEquals(0, cache.writes);
    }

    @Test
    void storesSuccessfulProviderResultsAfterCacheMiss() {
        FakeCache cache = new FakeCache();
        ProviderRequestGuard guard = new ProviderRequestGuard();
        ResearchMaterialGateway gateway = new ResearchMaterialGateway(
                Collections.singletonList(provider("NETWORK", 10, false)),
                new ProviderRoutePolicy(guard), guard, cache);

        ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "订单", 10));

        assertEquals(1, result.getMaterials().size());
        assertEquals(1, cache.writes);
    }

    private ResearchMaterial material(String id, String title) {
        ResearchMaterial value = new ResearchMaterial();
        value.setMaterialType(ResearchMaterialType.NEWS_FLASH);
        value.setExternalId(id);
        value.setTitle(title);
        value.setContent(title);
        value.setProviderCode("CACHE");
        value.setProviderFamily("CACHE");
        value.setSourceTier("T2");
        value.setUrl("https://example.com/" + id);
        return value;
    }

    private static final class FakeCache implements ResearchMaterialCacheRepository {
        private ResearchMaterialCacheEntry entry;
        private int reads;
        private int writes;

        private FakeCache() { }

        private FakeCache(ResearchMaterialCacheEntry entry) { this.entry = entry; }

        @Override
        public Optional<ResearchMaterialCacheEntry> get(String key) {
            reads++;
            return Optional.ofNullable(entry);
        }

        @Override
        public void put(String key, ResearchMaterialCacheEntry value, Duration ttl) {
            writes++;
            entry = value;
        }
    }

    private ResearchMaterialProvider provider(String code, int priority, boolean fail) {
        return new ResearchMaterialProvider() {
            public String providerCode() { return code; }
            public String providerFamily() { return code; }
            public int priority() { return priority; }
            public int batchLimit() { return 10; }
            public Duration minimumInterval() { return Duration.ZERO; }
            public Duration timeout() { return Duration.ofSeconds(1); }
            public java.util.Set<ResearchMaterialType> materialTypes() {
                return Collections.singleton(ResearchMaterialType.NEWS_FLASH);
            }
            public ProviderResult<java.util.List<ResearchMaterial>> fetch(
                    ResearchMaterialType type, ResearchMaterialRequest request) {
                if (fail) throw new ProviderContractException("HTTP_503", "busy", false);
                ResearchMaterial material = new ResearchMaterial();
                material.setMaterialType(type); material.setExternalId("same"); material.setTitle("订单增长");
                material.setContent("订单增长"); material.setProviderCode(code); material.setProviderFamily(code);
                material.setSourceTier("T2"); material.setUrl("https://example.com/" + code);
                return ProviderResult.of(Collections.singletonList(material), LocalDateTime.now(), "hash", Collections.emptyList());
            }
        };
    }
}
