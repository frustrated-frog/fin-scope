package com.finscope.web.controller;

import com.finscope.common.enums.marketpulse.MarketAiStatus;
import com.finscope.domain.marketpulse.ai.*;
import com.finscope.common.enums.marketpulse.MarketStructureDimension;
import com.finscope.service.marketpulse.MarketAiService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketAiControllerTest {
    @Test
    void readUsesIsoDatesAndOnlyPostRequestsGeneration() throws Exception {
        var service = mock(MarketAiService.class);
        var controller = new MarketAiController();
        ReflectionTestUtils.setField(controller, "service", service);
        var date = LocalDate.parse("2026-11-17");
        var view = new MarketAiView();
        view.setBusinessDate(date);
        view.setAsOf(date.atTime(15, 30));
        view.setStatus(MarketAiStatus.NOT_GENERATED);
        var score = new MarketStructureScore();
        score.setDimension(MarketStructureDimension.REPAIR_QUALITY);
        score.setBaselineScore(58);
        score.setFinalScore(58);
        var missing = new MarketStructureScore();
        missing.setDimension(MarketStructureDimension.EXPANSION);
        view.setStructure(java.util.List.of(score, missing));
        when(service.read(date)).thenReturn(view);
        when(service.request(date, true)).thenReturn(view);
        when(service.revalidate(date)).thenReturn(view);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/market-pulse/ai/2026-11-17"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.businessDate").value("2026-11-17"))
                .andExpect(jsonPath("$.data.asOf").value("2026-11-17T15:30"))
                .andExpect(jsonPath("$.data.structure[0].dimension").value("REPAIR_QUALITY"))
                .andExpect(jsonPath("$.data.structure[0].baselineScore").value(58))
                .andExpect(jsonPath("$.data.structure[0].finalScore").value(58))
                .andExpect(jsonPath("$.data.structure[1].baselineScore").isEmpty());
        verify(service, never()).request(any(), anyBoolean());
        mvc.perform(post("/api/market-pulse/ai/2026-11-17?retry=true")).andExpect(status().isOk());
        verify(service).request(date, true);
        mvc.perform(post("/api/market-pulse/ai/2026-11-17/revalidate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NOT_GENERATED"))
                .andExpect(jsonPath("$.data.responseText").doesNotExist())
                .andExpect(jsonPath("$.data.input").doesNotExist());
        verify(service).revalidate(date);
        verify(service, times(1)).request(any(), anyBoolean());
    }
}
