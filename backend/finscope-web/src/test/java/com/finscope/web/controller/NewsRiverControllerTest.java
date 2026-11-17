package com.finscope.web.controller;

import com.finscope.service.radar.NewsRiverService;
import com.finscope.domain.radar.*;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NewsRiverController.class)
@Import(FinScopeProperties.class)
class NewsRiverControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private NewsRiverService service;
    @Test
    void returnsActualFrameMetadataAndKeepsLongEventIdsAsStrings() throws Exception {
        NewsRiverOverview overview = new NewsRiverOverview();
        overview.setDate(LocalDate.parse("2026-11-17")); overview.setFrames(List.of()); overview.setBuckets(List.of());
        when(service.overview(LocalDate.parse("2026-11-17"))).thenReturn(overview);
        mvc.perform(get("/api/news/river?date=2026-11-17")).andExpect(status().isOk()).andExpect(jsonPath("$.data.frames").isArray());
        RadarReplayFrame frame = new RadarReplayFrame(); frame.setId("19"); frame.setPublishedAt(LocalDateTime.parse("2026-11-17T10:00:00"));
        RadarReplayItem item = new RadarReplayItem(); item.setEventId("6140854697134002116"); item.setTitle("当时标题");
        NewsRiverPage page = new NewsRiverPage(); page.setFrame(frame); page.setItems(List.of(item)); page.setTotal(1);
        when(service.frame(19L, "ALL", null, 0)).thenReturn(page);
        mvc.perform(get("/api/news/river/frame?batchId=19")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventId").value("6140854697134002116"))
                .andExpect(jsonPath("$.data.items[0].title").value("当时标题"));
    }
    @Test
    void rejectsMalformedDatesBeforeEnteringTheService() throws Exception {
        mvc.perform(get("/api/news/river?date=not-a-date")).andExpect(status().isBadRequest());
        verify(service, never()).overview(any());
    }
}
