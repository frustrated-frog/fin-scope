package com.finscope.service.marketpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPulseCandidateServiceTest {
    private MarketPulseCandidateService service;

    @BeforeEach
    void setUp() {
        service = new MarketPulseCandidateService();
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void excludesUnhealthyAndWeakSectorCandidatesInsteadOfFillingFiveSlots() {
        StockDiscoveryCandidate healthy = candidate("600001.SH", "HEALTHY", "ROBUST", 1, "医药生物");
        StockDiscoveryCandidate unhealthy = candidate("600002.SH", "DEGRADED", "ROBUST", 2, "医药生物");
        StockDiscoveryCandidate weak = candidate("600003.SH", "HEALTHY", "ROBUST", 3, "算力硬件");

        List<MarketPulseCandidate> values = service.assemble(new StockDiscoveryRun(),
                Arrays.asList(healthy, unhealthy, weak), Arrays.asList(
                        sector("医药生物", SectorRotationStage.ACCELERATING),
                        sector("算力硬件", SectorRotationStage.WEAK)));

        assertEquals(1, values.size());
        assertEquals("600001.SH", values.get(0).getInstrumentCode());
        assertTrue(values.get(0).getWhyNow().contains("医药生物"));
        assertTrue(values.get(0).getInvalidationConditions().size() >= 2);
    }

    private StockDiscoveryCandidate candidate(String code, String health, String conclusion,
                                               int rank, String sectorName) {
        StockDiscoveryCandidate value = new StockDiscoveryCandidate();
        value.setInstrumentCode(code);
        value.setName(code);
        value.setFinalRank(rank);
        value.setHealthStatus(health);
        value.setConclusion(conclusion);
        value.setCalibratedProbability(0.68D);
        value.setSectorNamesJson("[\"" + sectorName + "\"]");
        return value;
    }

    private SectorRotationItem sector(String name, SectorRotationStage stage) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode(name);
        value.setSectorName(name);
        value.setStage(stage);
        value.setRotationScore(stage == SectorRotationStage.WEAK ? 22 : 78);
        return value;
    }
}
