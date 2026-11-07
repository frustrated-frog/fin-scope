package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarTimelineEntryDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.mockito.ArgumentCaptor;

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
        when(repository.appendTimeline(eq(10L), any())).thenReturn(Collections.singletonList(stored));

        assertEquals(1, service.timeline(event, Collections.singletonList(signal),
                Collections.singletonList(evidence), interpretation).size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RadarTimelineEntryDraft>> entries = ArgumentCaptor.forClass(List.class);
        verify(repository).appendTimeline(eq(10L), entries.capture());
        assertEquals(List.of("event:10:first", "signal:2", "evidence:3", "interpretation:4"),
                entries.getValue().stream().map(RadarTimelineEntryDraft::getFingerprint).toList());
        assertEquals("公司发布电池", entries.getValue().get(1).getSummary());
        assertEquals(interpretation.getCompletedAt(), entries.getValue().get(3).getOccurredAt());
    }
}
