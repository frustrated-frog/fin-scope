package com.finscope.service.radar;

import com.finscope.dao.radar.RadarReplayRepository;
import com.finscope.domain.radar.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RadarReplayRecorderTest {
    @Test
    void recordsTheMaterializedCardTextInsteadOfLaterMutationsAndUsesActualObservationTime() {
        RadarReplayRepository repository = mock(RadarReplayRepository.class);
        RadarReplayRecorder recorder = new RadarReplayRecorder();
        ReflectionTestUtils.setField(recorder, "repository", repository);
        LocalDateTime at = LocalDateTime.parse("2026-11-17T10:35:00");
        ReflectionTestUtils.setField(recorder, "clock", Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        RadarEvent event = new RadarEvent(); event.setId(42L); event.setEventKey("key"); event.setCanonicalTitle("已发布标题");
        event.setDashboardCategory("TECHNOLOGY"); event.setFirstSeenAt(at.minusDays(1));
        var view = new ResearchRadarView(List.of(new ResearchRadarView.EventCard(event)), List.of(), List.of(), at);
        var observed = recorder.capture(List.of(event));
        event.setCanonicalTitle("后来才出现的标题");
        recorder.record("real:12", observed, view);
        ArgumentCaptor<List<RadarReplayItem>> ranking = ArgumentCaptor.forClass(List.class);
        verify(repository).savePublished(eq("real:12"), eq(at), eq(observed), ranking.capture());
        assertEquals("已发布标题", ranking.getValue().get(0).getTitle());
        assertEquals("TECHNOLOGY", ranking.getValue().get(0).getCategory());
        assertEquals("已发布标题", observed.get(0).getTitle());
    }
}
