package com.finscope.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainRevision;
import com.finscope.domain.industrychain.IndustryChainEventFeed;
import com.finscope.service.industrychain.IndustryChainEventService;
import com.finscope.service.industrychain.IndustryChainService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndustryChainControllerTest {

    @Test
    void createsAndReadsAnIndustryChainWorkspace() throws Exception {
        IndustryChainService service = mock(IndustryChainService.class);
        IndustryChain chain = new IndustryChain();
        chain.setId(7L);
        chain.setName("AI算力");
        IndustryChainRevision revision = new IndustryChainRevision();
        revision.setId(11L);
        revision.setChainId(7L);
        revision.setStatus("RUNNING");
        when(service.create("AI算力")).thenReturn(
                new IndustryChainService.Workspace(chain, revision, null));
        when(service.get(7L)).thenReturn(new IndustryChainService.Workspace(chain, revision, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new IndustryChainController(service)).build();

        mvc.perform(post("/api/industry-chains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                java.util.Collections.singletonMap("name", "AI算力"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chain.name").value("AI算力"))
                .andExpect(jsonPath("$.data.revision.status").value("RUNNING"));
        mvc.perform(get("/api/industry-chains/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chain.id").value(7));
        verify(service).create(eq("AI算力"));
    }

    @Test
    void readsAndRefreshesIndustryChainEvents() throws Exception {
        IndustryChainService chains = mock(IndustryChainService.class);
        IndustryChainEventService events = mock(IndustryChainEventService.class);
        IndustryChainEventFeed feed = new IndustryChainEventFeed();
        feed.setChainId(7L);
        feed.setHours(24);
        when(events.feed(7L, 24)).thenReturn(feed);
        when(events.refresh(7L)).thenReturn(new IndustryChainEventService.RefreshSummary(5, 2, 0, 3, null));
        IndustryChainController controller = new IndustryChainController(chains);
        ReflectionTestUtils.setField(controller, "eventService", events);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/industry-chains/7/events").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chainId").value(7))
                .andExpect(jsonPath("$.data.hours").value(24));
        mvc.perform(post("/api/industry-chains/7/events/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(2));
    }
}
