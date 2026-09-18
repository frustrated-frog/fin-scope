package com.finscope.service.attribution;

import java.time.LocalDate;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionResearchPlanFactoryTest {
    @Test
    void createsFiveBoundedTracksForStockResearch() {
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        instrument.setName("贵州茅台");
        instrument.setType("STOCK");

        AttributionResearchPlan plan = new AttributionResearchPlanFactory().create(instrument, 3.2D, LocalDate.parse("2026-09-18"));

        Set<String> tracks = new HashSet<String>();
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            tracks.add(track.getCode());
            assertTrue(track.getQueries().stream().allMatch(query -> query.contains("2026-09-18") && !query.contains("今日") && !query.contains("最新")));
            assertTrue(track.getMaxQueries() > 0);
            assertTrue(track.getSuccessCriteria().length() > 0);
            assertTrue(track.getQueries().size() > 0);
        }
        assertEquals(5, plan.getTracks().size());
        assertTrue(tracks.contains("COMPANY"));
        assertTrue(tracks.contains("INDUSTRY"));
        assertTrue(tracks.contains("MACRO"));
        assertTrue(tracks.contains("MARKET"));
        assertTrue(tracks.contains("COUNTER"));
        assertEquals(8, plan.getBudget().getMaxQueries());
        new AttributionPlanValidator().validate(plan);
    }

    @Test
    void createsFundExposureTrackForFundResearch() {
        Instrument instrument = new Instrument();
        instrument.setCode("000001");
        instrument.setName("测试基金");
        instrument.setType("FUND");

        AttributionResearchPlan plan = new AttributionResearchPlanFactory().create(instrument, -1.2D, LocalDate.parse("2026-09-18"));

        assertTrue(plan.hasTrack("FUND_EXPOSURE"));
        assertTrue(plan.hasTrack("COUNTER"));
    }
}
