package com.finscope.web.controller;

import com.finscope.common.enums.radar.RadarChangeKind;
import com.finscope.dao.radar.RadarChangeRepository;
import com.finscope.domain.radar.RadarRecentChange;
import com.finscope.service.radar.RadarChangeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RadarChangeControllerTest {
    @Test
    void usesLocalReadOnlyHistoryAndPreservesWindowWhenPaging() throws Exception {
        var repo = mock(RadarChangeRepository.class);
        var service = new RadarChangeService();
        ReflectionTestUtils.setField(service, "repository", repo);
        var now = LocalDateTime.of(2026, 11, 18, 12, 0);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        var controller = new RadarChangeController();
        ReflectionTestUtils.setField(controller, "service", service);
        var row = new RadarRecentChange();
        row.setEventId("90071992547409937");
        row.setKind(RadarChangeKind.SPREAD);
        when(repo.recent(anyString(), anyString(), anyString(), anyString(), anyString(), eq(41), anyInt()))
                .thenReturn(Collections.nCopies(41, row));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/research-radar/changes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(40)).andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.items[0].eventId").value("90071992547409937"));
        mvc.perform(get("/api/research-radar/changes").param("minutes", "60").param("asOf", now.minusMinutes(2).toString())
                .param("kind", "SPREAD").param("page", "1")).andExpect(status().isOk());
        verify(repo).recent(eq("2026-11-18T10:58:00.000000000"),eq("2026-11-18T11:58:00.000000000"),anyString(),anyString(),eq("SPREAD"),eq(41),eq(40));
        assertThrows(IllegalArgumentException.class, () -> service.recent(45, null, now, 0));
        assertThrows(IllegalArgumentException.class, () -> service.recent(30, null, now.plusDays(1), 0));
        assertThrows(IllegalArgumentException.class, () -> service.recent(30, null, null, 1));
    }
}
