package com.finscope.service.attribution;

import java.time.LocalDate;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionResearchPlanFactoryTest {
    private AttributionResearchPlanFactory factory() {
        AttributionResearchPlanFactory factory = new AttributionResearchPlanFactory();
        org.springframework.test.util.ReflectionTestUtils.setField(factory, "tradingCalendarClient",
                org.mockito.Mockito.mock(com.finscope.rpc.quote.PythonTradingCalendarClient.class));
        return factory;
    }

    @Test
    void createsFiveBoundedTracksForStockResearch() {
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        instrument.setName("贵州茅台");
        instrument.setType("STOCK");

        AttributionResearchPlan plan = factory().create(instrument, 3.2D, LocalDate.parse("2026-09-18"));

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

        AttributionResearchPlan plan = factory().create(instrument, -1.2D, LocalDate.parse("2026-09-18"));

        assertTrue(plan.hasTrack("FUND_EXPOSURE"));
        assertTrue(plan.hasTrack("COUNTER"));
    }
    @Test
    void expandsNaturalDaySearchWindowAcrossVerifiedHoliday() {
        Instrument instrument = new Instrument();
        instrument.setType("STOCK");
        instrument.setCode("600519");
        AttributionResearchPlanFactory factory = factory();
        com.finscope.rpc.quote.PythonTradingCalendarClient calendar =
                org.mockito.Mockito.mock(com.finscope.rpc.quote.PythonTradingCalendarClient.class);
        org.mockito.Mockito.when(calendar.previousSession(LocalDate.parse("2026-10-08")))
                .thenReturn(LocalDate.parse("2026-09-30"));
        org.springframework.test.util.ReflectionTestUtils.setField(factory, "tradingCalendarClient", calendar);
        AttributionResearchPlan plan = factory.create(instrument, 2D, LocalDate.parse("2026-10-08"));
        assertEquals("2026-09-30", plan.getEvidenceStartDate());
        assertTrue(plan.getTracks().stream().flatMap(track -> track.getQueries().stream())
                .allMatch(query -> query.contains("2026-09-30 至 2026-10-08")));
        org.mockito.Mockito.when(calendar.previousSession(LocalDate.parse("2026-10-08")))
                .thenThrow(new IllegalStateException("unavailable"));
        assertEquals("2026-10-05", factory.create(instrument, 2D, LocalDate.parse("2026-10-08")).getEvidenceStartDate());
    }

}
