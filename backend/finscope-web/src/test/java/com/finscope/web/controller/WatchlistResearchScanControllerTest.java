package com.finscope.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finscope.common.enums.watchlistresearch.ResearchScanStage;
import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.domain.watchlistresearch.*;
import com.finscope.service.watchlistresearch.WatchlistResearchScanService;
import com.finscope.web.response.WatchlistResearchScanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WatchlistResearchScanControllerTest {
    private WatchlistResearchScanService service;
    private WatchlistResearchScanController controller;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = mock(WatchlistResearchScanService.class);
        controller = new WatchlistResearchScanController();
        ReflectionTestUtils.setField(controller, "service", service);
    }

    @Test
    void getAndPostUseTheSameUseCaseWithDifferentRefreshSemantics() {
        when(service.view(false)).thenReturn(new ResearchScanView());
        when(service.view(true)).thenReturn(new ResearchScanView());
        assertTrue(controller.view().isSuccess());
        assertTrue(controller.refresh().isSuccess());
        verify(service).view(false);
        verify(service).view(true);
    }

    @Test
    void httpRoutesExposeTheUnifiedEnvelopeForReadRefreshAndHistory() throws Exception {
        when(service.view(false)).thenReturn(new ResearchScanView());
        when(service.view(true)).thenReturn(new ResearchScanView());
        ResearchScanRun run = new ResearchScanRun();
        run.setId("one");
        when(service.history("one")).thenReturn(run);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        mvc.perform(get("/api/watchlist/research-scan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.history").isArray());
        mvc.perform(post("/api/watchlist/research-scan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/watchlist/research-scan/one")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("one"));
    }

    @Test
    void historyMapsFrozenDatesNullsAndNumericFieldNamesWithoutPersistenceInternals() throws Exception {
        ResearchScanRun source = new ResearchScanRun();
        source.setId("run-id");
        source.setMembershipKey("private-membership-key");
        source.setScanDate(LocalDate.of(2026, 11, 6));
        source.setStartedAt(Instant.parse("2026-11-07T02:00:00Z"));
        source.setStatus(ResearchScanStatus.PARTIAL);
        source.setStage(ResearchScanStage.COMPLETED);
        WatchlistResearchSnapshot snapshot = new WatchlistResearchSnapshot();
        StockStrengthAnalysis strength = new StockStrengthAnalysis();
        strength.setCode("605058");
        strength.setFitQuality(.82);
        strength.setExcessReturnPp(4.2);
        snapshot.getStrengths().add(strength);
        ResearchFinding finding = new ResearchFinding();
        finding.setKey("signal");
        finding.setMagnitude(42.0);
        finding.setNewlyObserved(true);
        snapshot.getFindings().add(finding);
        source.setSnapshot(snapshot);
        when(service.history("run-id")).thenReturn(source);
        JsonNode data = json.readTree(json.writeValueAsString(controller.history("run-id"))).path("data");
        assertEquals("PARTIAL", data.path("status").asText());
        assertEquals("COMPLETED", data.path("stage").asText());
        assertEquals("2026-11-06", data.path("scanDate").asText());
        assertEquals("2026-11-07T02:00:00Z", data.path("startedAt").asText());
        assertFalse(data.has("membershipKey"));
        assertEquals(.82, data.path("snapshot").path("strengths").get(0).path("fitQuality").asDouble());
        assertTrue(data.path("snapshot").path("strengths").get(0).path("beta").isNull());
        assertTrue(data.path("snapshot").path("findings").get(0).path("newlyObserved").asBoolean());
        assertFalse(data.path("snapshot").path("findings").get(0).has("magnitude"));
    }

    @Test
    void absentCurrentSnapshotAndHistorySummariesRemainExplicit() {
        ResearchScanView view = new ResearchScanView();
        ResearchScanRun history = new ResearchScanRun();
        history.setId("history");
        view.setHistory(List.of(history));
        WatchlistResearchScanResponse response = WatchlistResearchScanResponse.of(view);
        assertNull(response.getLatest());
        assertNull(response.getActive());
        assertEquals("history", response.getHistory().get(0).getId());
        assertNull(response.getHistory().get(0).getSnapshot());
    }
}
