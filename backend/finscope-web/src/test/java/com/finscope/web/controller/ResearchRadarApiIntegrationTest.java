package com.finscope.web.controller;

import com.finscope.service.radar.ResearchRadarService;
import com.finscope.service.radar.ResearchRadarView;
import com.finscope.service.radar.RadarEventWorkspaceService;
import com.finscope.service.radar.RadarResearchLinkService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void readsTheCurrentRadarWorkspaceInsteadOfAStaleRankedSnapshot() throws Exception {
        when(snapshots.read(eq("radar"), eq("category=ALL&watchlist=false&limit=20&state=ALL")))
                .thenReturn(Optional.of(new ObjectMapper().findAndRegisterModules()
                        .valueToTree(ResearchRadarView.empty(LocalDateTime.of(2026, 7, 31, 16, 0)))));
        RadarEvent event = new RadarEvent(); event.setId(10L); event.setCanonicalTitle("当前事件");
        ResearchRadarView current = new ResearchRadarView(Collections.singletonList(
                new ResearchRadarView.EventCard(event)), Collections.emptyList(), Collections.emptyList(),
                LocalDateTime.of(2026, 8, 7, 18, 0));
        when(service.loadStored("ALL", false, 20, "ALL")).thenReturn(current);

        mvc.perform(get("/api/research-radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events").isArray())
                .andExpect(jsonPath("$.data.latestChanges").doesNotExist())
                .andExpect(jsonPath("$.data.liveItems").isArray())
                .andExpect(jsonPath("$.data.overview.eventCount").value(1));
        verify(service).loadStored("ALL", false, 20, "ALL");
        verify(snapshots, never()).read(any(), any());
    }

    @Test
    void rebuildsTheRadarFromStoredEventsWhenAWorkspaceChangeInvalidatesItsSnapshot() throws Exception {
        when(snapshots.read(eq("radar"), eq("category=ALL&watchlist=false&limit=20&state=ALL")))
                .thenReturn(Optional.empty());
        RadarEvent event = new RadarEvent();
        event.setId(10L); event.setCanonicalTitle("宁德时代发布新电池"); event.setStatus("ACTIVE");
        RadarEventWorkspace.Summary state = new RadarEventWorkspace.Summary();
        state.setEventId(10L); state.setFollowed(true);
        ResearchRadarView view = new ResearchRadarView(Collections.singletonList(
                new ResearchRadarView.EventCard(event, null, state)), Collections.emptyList(),
                Collections.emptyList(), LocalDateTime.of(2026, 8, 7, 14, 0));
        when(service.loadStored("ALL", false, 20, "ALL")).thenReturn(view);

        mvc.perform(get("/api/research-radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].followed").value(true));

        verify(service).loadStored("ALL", false, 20, "ALL");
    }

    @Test
    void returnsThePersistedFollowListWithoutReadingTheRankedSnapshotCache() throws Exception {
        RadarEvent event = new RadarEvent();
        event.setId(10L); event.setCanonicalTitle("已关注事件"); event.setStatus("ACTIVE");
        RadarEventWorkspace.Summary state = new RadarEventWorkspace.Summary();
        state.setEventId(10L); state.setFollowed(true);
        ResearchRadarView view = new ResearchRadarView(Collections.singletonList(
                new ResearchRadarView.EventCard(event, null, state)), Collections.emptyList(),
                Collections.emptyList(), LocalDateTime.of(2026, 8, 7, 14, 0));
        when(service.loadFollowed(20)).thenReturn(view);

        mvc.perform(get("/api/research-radar/followed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].id").value(10))
                .andExpect(jsonPath("$.data.events[0].followed").value(true));

        verify(service).loadFollowed(20);
        verify(snapshots, never()).read(any(), any());
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
