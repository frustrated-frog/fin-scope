package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarEventTimelineServiceTest {
    @Test
    void materializesDeterministicDetailTimelineAndReturnsStoredOrder() {
        RadarEventWorkspaceRepository repository = mock(RadarEventWorkspaceRepository.class);
        RadarEventTimelineService service = new RadarEventTimelineService(repository);
        RadarEvent event = new RadarEvent(); event.setId(10L); event.setEventKey("battery");
        event.setCanonicalTitle("电池发布"); event.setFirstSeenAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        RadarSignal signal = new RadarSignal(); signal.setId(2L); signal.setTitle("公司发布电池");
        signal.setPublishedAt(LocalDateTime.of(2026, 8, 1, 10, 5));
        RadarEvidence evidence = new RadarEvidence(); evidence.setId(3L); evidence.setTitle("交易所公告");
        evidence.setPublishedAt(LocalDateTime.of(2026, 8, 1, 10, 10));
        RadarEventInterpretation interpretation = new RadarEventInterpretation(); interpretation.setId(4L);
        interpretation.setStatus("SUCCESS"); interpretation.setCompletedAt(LocalDateTime.of(2026, 8, 1, 10, 15));
        RadarEventWorkspace.TimelineEntry stored = new RadarEventWorkspace.TimelineEntry(); stored.setEventId(10L);
        when(repository.findTimeline(10L)).thenReturn(Collections.singletonList(stored));

        assertEquals(1, service.timeline(event, Collections.singletonList(signal),
                Collections.singletonList(evidence), interpretation).size());

        verify(repository).appendTimeline(eq(10L), any(), eq("FIRST_SEEN"), eq("事件首次进入雷达"),
                any(), eq("EVENT"), eq(10L), eq(event.getFirstSeenAt()));
        verify(repository).appendTimeline(eq(10L), any(), eq("SIGNAL"), eq("新增来源消息"),
                eq("公司发布电池"), eq("SIGNAL"), eq(2L), eq(signal.getPublishedAt()));
        verify(repository).appendTimeline(eq(10L), any(), eq("EVIDENCE"), eq("新增研究证据"),
                eq("交易所公告"), eq("EVIDENCE"), eq(3L), eq(evidence.getPublishedAt()));
        verify(repository).appendTimeline(eq(10L), any(), eq("INTERPRETATION"), eq("事件解读已更新"),
                any(), eq("INTERPRETATION"), eq(4L), eq(interpretation.getCompletedAt()));
    }
}
