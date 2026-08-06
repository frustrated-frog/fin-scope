package com.finscope.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.service.dashboard.DashboardService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(FinScopeProperties.class)
class DashboardHotspotControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private DashboardService dashboardService;
    @MockBean private ViewSnapshotCacheService snapshots;

    @Test
    void readsThePrewarmedHotspotPayloadWithoutLoadingDashboardSummary() throws Exception {
        when(snapshots.read(eq("dashboard"), eq("hotspots"))).thenReturn(Optional.of(
                new ObjectMapper().readTree("[{\"categoryCode\":\"FINANCE\",\"items\":[]}]")));

        mvc.perform(get("/api/dashboard/hotspots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryCode").value("FINANCE"));

        verifyNoInteractions(dashboardService);
    }
}
