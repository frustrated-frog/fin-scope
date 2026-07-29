package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "FINSCOPE_LIVE_SOURCE_TESTS", matches = "true")
class ThsNewsResearchMaterialProviderLiveTest {
    @Test
    void readsCurrentRealtimeNewsContract() {
        List<ResearchMaterial> materials = new ThsRealtimeNewsResearchMaterialProvider(
                new JdkAcquisitionRuntime(), new ObjectMapper())
                .fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 10))
                .getData();

        assertUsable(materials);
    }

    @Test
    void readsCurrentHeadlineDigestContract() {
        List<ResearchMaterial> materials = new ThsHeadlineNewsResearchMaterialProvider(
                new JdkAcquisitionRuntime(), new ObjectMapper())
                .fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 10))
                .getData();

        assertUsable(materials);
    }

    private void assertUsable(List<ResearchMaterial> materials) {
        assertFalse(materials.isEmpty());
        assertTrue(materials.size() <= 10);
        for (ResearchMaterial material : materials) {
            assertFalse(material.getTitle().trim().isEmpty());
            assertFalse(material.getContent().trim().isEmpty());
            assertFalse(material.getExternalId().trim().isEmpty());
            assertTrue(material.getUrl().isEmpty() || material.getUrl().startsWith("https://"));
        }
    }
}
