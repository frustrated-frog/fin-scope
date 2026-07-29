package com.finscope.service.research.material;

import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
