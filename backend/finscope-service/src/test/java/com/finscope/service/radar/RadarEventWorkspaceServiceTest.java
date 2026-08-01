package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarEventWorkspaceServiceTest {
    private RadarEventWorkspaceRepository workspace;
    private RadarRepository radar;
    private RadarEventWorkspaceService service;

    @BeforeEach
    void setUp() {
        workspace = mock(RadarEventWorkspaceRepository.class);
        radar = mock(RadarRepository.class);
        service = new RadarEventWorkspaceService(workspace, radar);
    }

    @Test
    void openingAnEventMarksItReadAndCreatesTheDefaultObservation() {
        RadarEvent event = event();
        RadarEventWorkspace.State state = new RadarEventWorkspace.State(); state.setEventId(10L);
        when(workspace.updateState(eq(10L), eq(true), eq(null), eq(null), anyString())).thenReturn(state);
        when(workspace.ensureDefaultObservation(10L, "跟踪订单、指引与后续公告变化"))
                .thenReturn(Collections.<RadarEventWorkspace.Observation>emptyList());

        RadarEventWorkspaceService.OpenedEvent opened = service.open(event);

        assertEquals(10L, opened.getState().getEventId());
        verify(workspace).ensureDefaultObservation(10L, "跟踪订单、指引与后续公告变化");
    }

    @Test
    void rejectsUpdatesForMissingEvents() {
        when(radar.findEvent(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.updateState(99L, true, true, "ACTIVE"));
    }

    private RadarEvent event() {
        RadarEvent event = new RadarEvent(); event.setId(10L); event.setEventKey("battery-release");
        event.setCanonicalTitle("宁德时代发布新电池"); event.setNextObservation("跟踪订单、指引与后续公告变化");
        event.setSignalCount(2); event.setSourceCount(2); return event;
    }
}
