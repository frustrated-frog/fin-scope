package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void createsOneNotificationWhenAFollowedEventChanges() {
        RadarEvent event = event(); event.setLastSeenAt(java.time.LocalDateTime.of(2026,8,1,18,0));
        RadarEventWorkspace.Summary summary = new RadarEventWorkspace.Summary(); summary.setEventId(10L);
        summary.setFollowed(true); summary.setLastViewedFingerprint("older-version");
        Map<Long,RadarEventWorkspace.Summary> summaries=new LinkedHashMap<Long,RadarEventWorkspace.Summary>();summaries.put(10L,summary);
        when(workspace.createNotification(eq(10L),eq("FOLLOWED_EVENT_CHANGED"),anyString(),eq("关注事件出现新变化"),eq("宁德时代发布新电池"))).thenReturn(true);

        service.createChangeNotifications(Collections.singletonList(event),summaries);

        assertEquals(1,summary.getUnreadNotificationCount());
        verify(workspace).createNotification(eq(10L),eq("FOLLOWED_EVENT_CHANGED"),anyString(),eq("关注事件出现新变化"),eq("宁德时代发布新电池"));
    }

    @Test
    void marksPreviouslyViewedEventUnreadWhenItsVersionChanges() {
        RadarEventWorkspace.Summary summary = new RadarEventWorkspace.Summary();
        summary.setReadAt(java.time.LocalDateTime.of(2026,8,1,10,0));
        summary.setLastViewedFingerprint("older-version");

        service.reconcileRead(event(), summary);

        assertFalse(summary.isRead());
    }

    @Test
    void prependsDeterministicDailySummary() {
        when(workspace.countNewEventsOn(org.mockito.ArgumentMatchers.any())).thenReturn(4);
        when(workspace.countFollowedChangesOn(org.mockito.ArgumentMatchers.any())).thenReturn(2);
        when(workspace.countOpenObservations()).thenReturn(3);
        when(workspace.findNotifications(30)).thenReturn(Collections.emptyList());

        RadarEventWorkspaceService.NotificationCenter center = service.notifications(30);

        assertEquals("DAILY_SUMMARY", center.getItems().get(0).getNotificationType());
        assertEquals("新增事件 4 · 关注变化 2 · 待处理观察 3", center.getItems().get(0).getMessage());
        assertEquals(4, center.getTodayCount());
    }

    private RadarEvent event() {
        RadarEvent event = new RadarEvent(); event.setId(10L); event.setEventKey("battery-release");
        event.setCanonicalTitle("宁德时代发布新电池"); event.setNextObservation("跟踪订单、指引与后续公告变化");
        event.setSignalCount(2); event.setSourceCount(2); return event;
    }
}
