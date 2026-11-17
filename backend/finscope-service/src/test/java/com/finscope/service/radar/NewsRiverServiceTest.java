package com.finscope.service.radar;

import com.finscope.dao.radar.RadarReplayRepository;
import com.finscope.domain.radar.RadarReplayFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsRiverServiceTest {
    private NewsRiverService service;
    private RadarReplayRepository repository;
    private final LocalDateTime at = LocalDateTime.parse("2026-11-17T10:15:00.000000001");
    @BeforeEach
    void setup() {
        service = new NewsRiverService(); repository = mock(RadarReplayRepository.class);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        RadarReplayFrame frame = new RadarReplayFrame(); frame.setId("10"); frame.setPublishedAt(at);
        when(repository.frame(10)).thenReturn(Optional.of(frame));
        when(repository.observed(any(), any(), anyString(), anyInt())).thenReturn(List.of());
        when(repository.ranking(anyLong(), anyString())).thenReturn(List.of());
    }
    @Test
    void rejectsFutureExpiredAndMalformedTimelineSelections() {
        assertThrows(IllegalArgumentException.class, () -> service.overview(at.toLocalDate().plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.overview(at.toLocalDate().minusDays(7)));
        assertThrows(IllegalArgumentException.class, () -> service.frame(10, "UNKNOWN", null, 0));
        assertThrows(IllegalArgumentException.class, () -> service.frame(10, "ALL", at.plusMinutes(15).withNano(0), 0));
        assertThrows(IllegalArgumentException.class, () -> service.frame(10, "ALL", at.withMinute(1).withNano(0), 0));
        assertThrows(IllegalArgumentException.class, () -> service.frame(10, "ALL", at.minusDays(1).withMinute(0).withNano(0), 0));
        assertThrows(IllegalArgumentException.class, () -> service.frame(10, "ALL", null, -1));
        assertThrows(IllegalArgumentException.class, () -> service.frame(10, "ALL", null, 1));
        assertThrows(IllegalArgumentException.class, () -> service.frame(99, "ALL", null, 0));
    }
    @Test
    void clipsBucketToTheSelectedPublicationTimeInsteadOfUsingLaterArrivals() {
        LocalDateTime bucket = at.withMinute(0).withSecond(0).withNano(0);
        service.frame(10, "FINANCE", bucket, 0);
        verify(repository).observed(bucket, at, "FINANCE", 0);
        verify(repository).observedCount(bucket, at, "FINANCE");
        service.frame(10, "ALL", null, 0);
        verify(repository).ranking(10, "ALL");
    }
}
