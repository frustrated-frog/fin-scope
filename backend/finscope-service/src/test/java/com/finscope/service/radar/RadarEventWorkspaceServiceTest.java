package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.service.cache.ViewRevisionService;
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
import static org.mockito.Mockito.never;
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
    void openingAnEventMarksItReadWithoutCreatingPersistentObservations() {
        RadarEvent event = event();
        RadarEventWorkspace.State state = new RadarEventWorkspace.State(); state.setEventId(10L);
        when(workspace.updateState(eq(10L), eq(true), eq(null), eq(null), anyString())).thenReturn(state);

        RadarEventWorkspaceService.OpenedEvent opened = service.open(event);

        assertEquals(10L, opened.getState().getEventId());
        verify(workspace, never()).ensureDefaultObservation(eq(10L), anyString());
    }

    @Test
    void reopeningAnAlreadyReadEventDoesNotInvalidateTheRadarSnapshot() {
        ViewRevisionService revisions = mock(ViewRevisionService.class);
        service = new RadarEventWorkspaceService(workspace, radar, null, revisions);
        RadarEventWorkspace.State previous = new RadarEventWorkspace.State(); previous.setEventId(10L); previous.setRead(true);
        RadarEventWorkspace.State updated = new RadarEventWorkspace.State(); updated.setEventId(10L); updated.setRead(true);
        when(workspace.findState(10L)).thenReturn(previous);
        when(workspace.updateState(eq(10L), eq(true), eq(null), eq(null), anyString())).thenReturn(updated);

        service.open(event());

        verify(revisions, never()).invalidate("radar");
    }

    @Test
    void invalidatesTheRadarSnapshotAfterFollowingAnEvent() {
        ViewRevisionService revisions = mock(ViewRevisionService.class);
        service = new RadarEventWorkspaceService(workspace, radar, null, revisions);
        RadarEventWorkspace.State updated = new RadarEventWorkspace.State(); updated.setEventId(10L); updated.setFollowed(true);
        when(radar.findEvent(10L)).thenReturn(Optional.of(event()));
        when(workspace.updateState(eq(10L), eq(false), eq(null), eq(true), anyString())).thenReturn(updated);

        service.updateState(10L, null, true, null);

        verify(revisions).invalidate("radar");
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
        when(workspace.createNotification(eq(10L),eq("FOLLOWED_EVENT_CHANGED"),anyString(),eq("临时关注事件出现新变化"),eq("宁德时代发布新电池"))).thenReturn(true);

        service.createChangeNotifications(Collections.singletonList(event),summaries);

        assertEquals(1,summary.getUnreadNotificationCount());
        verify(workspace).createNotification(eq(10L),eq("FOLLOWED_EVENT_CHANGED"),anyString(),eq("临时关注事件出现新变化"),eq("宁德时代发布新电池"));
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
        assertEquals("新增事件 4 · 临时关注变化 2", center.getItems().get(0).getMessage());
        assertEquals(4, center.getTodayCount());
    }

    private RadarEvent event() {
        RadarEvent event = new RadarEvent(); event.setId(10L); event.setEventKey("battery-release");
        event.setCanonicalTitle("宁德时代发布新电池"); event.setNextObservation("跟踪订单、指引与后续公告变化");
        event.setSignalCount(2); event.setSourceCount(2); return event;
    }
}
