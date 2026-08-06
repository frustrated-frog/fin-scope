package com.finscope.web.controller;

import com.finscope.service.radar.ResearchRadarService;
import com.finscope.service.radar.ResearchRadarView;
import com.finscope.service.radar.RadarEventWorkspaceService;
import com.finscope.service.radar.RadarResearchLinkService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResearchRadarController.class)
@Import(FinScopeProperties.class)
class ResearchRadarApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockBean private ResearchRadarService service;
    @MockBean private RadarEventWorkspaceService workspaceService;
    @MockBean private RadarResearchLinkService researchLinkService;
    @MockBean private ViewSnapshotCacheService snapshots;

    @Test
    void returnsUnifiedRadarEnvelope() throws Exception {
        when(service.loadStored("ALL", false, 20, "ALL")).thenReturn(ResearchRadarView.empty(LocalDateTime.of(2026, 7, 31, 16, 0)));
        when(snapshots.readOrLoad(any(), any(), any(), any())).thenAnswer(invocation ->
                new ObjectMapper().findAndRegisterModules().valueToTree(((Supplier<?>) invocation.getArgument(3)).get()));

        mvc.perform(get("/api/research-radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events").isArray())
                .andExpect(jsonPath("$.data.latestChanges").isArray())
                .andExpect(jsonPath("$.data.liveItems").isArray())
                .andExpect(jsonPath("$.data.overview.eventCount").value(0));
        verify(service).loadStored("ALL", false, 20, "ALL");
    }

    @Test
    void submitsEventInterpretationWithoutWaitingForCompletion() throws Exception {
        ResearchRadarView.InterpretationView queued = ResearchRadarView.InterpretationView.queued(10L);
        when(service.requestInterpretation(10L)).thenReturn(queued);

        mvc.perform(post("/api/research-radar/events/10/interpretation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(10))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verify(service).requestInterpretation(10L);
    }
}
