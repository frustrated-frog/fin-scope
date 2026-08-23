package com.finscope.web.controller;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import com.finscope.domain.marketpulse.MarketPulseBackfillResult;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.DailyMarketReview;
import com.finscope.domain.marketpulse.MarketPulseHistoryPoint;
import com.finscope.service.marketpulse.MarketPulseService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketPulseControllerTest {
    private MockMvc mockMvc;
    private MarketPulseService service;

    @BeforeEach
    void setUp() {
        service = mock(MarketPulseService.class);
        MarketPulseController controller = new MarketPulseController();
        ReflectionTestUtils.setField(controller, "service", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void exposesLatestHistoryAndRefreshContracts() throws Exception {
        MarketPulseWorkspace workspace = new MarketPulseWorkspace();
        workspace.setBusinessDate(LocalDate.of(2026, 8, 21));
        workspace.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        MarketBreadthSnapshot breadth = new MarketBreadthSnapshot();
        breadth.setBusinessDate(LocalDate.of(2026, 8, 21));
        breadth.setAdvanceCount(3200);
        breadth.setDeclineCount(1800);
        breadth.setFlatCount(100);
        breadth.setValidCount(5100);
        breadth.setAdvanceRatio(3200D / 5100D);
        workspace.setBreadth(breadth);
        DailyMarketReview review = new DailyMarketReview();
        review.setBusinessDate(LocalDate.of(2026, 8, 21));
        review.setHeadline("急跌后缩量修复，反弹持续性仍需量能确认");
        workspace.setDailyReview(review);
        MarketPulseHistoryPoint history = new MarketPulseHistoryPoint();
        history.setBusinessDate(LocalDate.of(2026, 8, 21));
        history.setHeadline(review.getHeadline());
        workspace.setHistoryPoints(Arrays.asList(history));
        when(service.latest()).thenReturn(workspace);
        when(service.dates(20)).thenReturn(Arrays.asList(LocalDate.of(2026, 8, 21)));
        MarketPulseRefreshResult refresh = new MarketPulseRefreshResult();
        refresh.setStatus("SUCCEEDED");
        when(service.refresh()).thenReturn(refresh);

        mockMvc.perform(get("/api/market-pulse/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessDate").value("2026-08-21"))
                .andExpect(jsonPath("$.data.breadth.advanceCount").value(3200))
                .andExpect(jsonPath("$.data.dailyReview.headline").value(review.getHeadline()))
                .andExpect(jsonPath("$.data.historyPoints[0].businessDate").value("2026-08-21"));
        mockMvc.perform(get("/api/market-pulse/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("2026-08-21"));
        mockMvc.perform(post("/api/market-pulse/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void exposesBoundedBackfillContract() throws Exception {
        MarketPulseBackfillResult result = new MarketPulseBackfillResult();
        result.setStatus("SUCCEEDED");
        MarketPulseRefreshResult day = new MarketPulseRefreshResult();
        day.setBusinessDate(LocalDate.of(2026, 8, 17));
        day.setStatus("SUCCEEDED");
        result.setResults(Arrays.asList(day));
        when(service.backfill(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 21)))
                .thenReturn(result);

        mockMvc.perform(post("/api/market-pulse/backfill")
                        .param("startDate", "2026-08-17")
                        .param("endDate", "2026-08-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.results[0].businessDate").value("2026-08-17"));
    }
}
