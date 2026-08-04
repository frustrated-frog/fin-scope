package com.finscope.web.controller;

import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.service.majorevent.MajorEventService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MajorEventController.class)
@Import(FinScopeProperties.class)
class MajorEventControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private MajorEventService service;

    @Test
    void createsLiveNewsSnapshotThroughTheStandardEnvelope() throws Exception {
        MajorEvent event = new MajorEvent();
        event.setId(1L);
        event.setOriginType("NEWS_ITEM");
        event.setOriginKey("CLS:1");
        event.setTitle("降准");
        event.setOccurredDate(LocalDate.of(2026, 8, 4));
        when(service.create(any())).thenReturn(event);

        mvc.perform(post("/api/major-events")
                        .contentType("application/json")
                        .content("{\"originType\":\"NEWS_ITEM\",\"originKey\":\"CLS:1\",\"title\":\"降准\",\"occurredDate\":\"2026-08-04\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.originType").value("NEWS_ITEM"));
    }
}
