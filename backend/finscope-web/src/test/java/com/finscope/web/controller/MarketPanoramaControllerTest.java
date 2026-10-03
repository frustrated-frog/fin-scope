package com.finscope.web.controller;

import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.service.marketpulse.MarketPanoramaService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketPanoramaControllerTest {
    @Test
    void exposesIsoDatesAndHandlesMissingOrInvalidDate() throws Exception {
        var service = mock(MarketPanoramaService.class);
        var controller = new MarketPanoramaController();
        ReflectionTestUtils.setField(controller, "service", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        var day = new MarketPulseWorkspace();
        day.setBusinessDate(LocalDate.of(2026, 9, 30));
        when(service.history(day.getBusinessDate(), 60)).thenReturn(List.of(day));
        mvc.perform(get("/api/market-pulse/panorama").param("businessDate", "2026-09-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].businessDate").value("2026-09-30"))
                .andExpect(jsonPath("$.data[0].indices").isArray());
        mvc.perform(get("/api/market-pulse/panorama")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/market-pulse/panorama").param("businessDate", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
