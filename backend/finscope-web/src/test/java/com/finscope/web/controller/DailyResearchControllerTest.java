package com.finscope.web.controller;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.domain.marketpulse.DailyResearchSnapshot;
import com.finscope.service.marketpulse.DailyResearchService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DailyResearchControllerTest {
    @Test
    void exposesIsoDatesEmptySamplesAndValidatesDateInput() throws Exception {
        var service = mock(DailyResearchService.class);
        var controller = new DailyResearchController();
        ReflectionTestUtils.setField(controller, "service", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        var snapshot = new DailyResearchSnapshot();
        snapshot.setBusinessDate(LocalDate.of(2026, 9, 11));
        snapshot.setSelectionDate(LocalDate.of(2026, 9, 10));
        snapshot.setSourceCode("LOCAL_DAILY_BAR_PANEL");
        snapshot.setQualityStatus(MarketPulseQualityStatus.UNAVAILABLE);
        snapshot.setSampleCount(0);
        snapshot.setStocks(List.of());
        snapshot.setGroups(List.of());
        snapshot.setWarnings(List.of("本地样本暂缺"));
        when(service.query(snapshot.getBusinessDate())).thenReturn(snapshot);
        mvc.perform(get("/api/market-pulse/research/2026-09-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessDate").value("2026-09-11"))
                .andExpect(jsonPath("$.data.selectionDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.qualityStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.stocks").isEmpty());
        mvc.perform(get("/api/market-pulse/research/not-a-date")).andExpect(status().is4xxClientError());
        verify(service, times(1)).query(any());
    }
}
