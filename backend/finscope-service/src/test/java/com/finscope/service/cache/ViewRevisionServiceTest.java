package com.finscope.service.cache;

import com.finscope.dao.cache.VersionedViewCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewRevisionServiceTest {
    @Test
    void invalidationPublishesTheNewRevisionOnlyAfterCacheVersionChanges() {
        VersionedViewCacheRepository cache = mock(VersionedViewCacheRepository.class);
        ViewRevisionPublisher publisher = mock(ViewRevisionPublisher.class);
        when(cache.invalidateAndGetRevision("radar")).thenReturn(9L);
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T01:02:03Z"), ZoneId.of("Asia/Shanghai"));
        ViewRevisionService service = new ViewRevisionService(cache, publisher, clock);

        ViewRevision revision = service.invalidate("RADAR");

        assertEquals("radar", revision.getScope());
        assertEquals(9L, revision.getRevision());
        verify(cache).invalidateAndGetRevision("radar");
        verify(publisher).publish(revision);
    }

    @Test
    void readsCurrentRevisionsForFallbackReconciliation() {
        VersionedViewCacheRepository cache = mock(VersionedViewCacheRepository.class);
        ViewRevisionService service = new ViewRevisionService(cache, mock(ViewRevisionPublisher.class), Clock.systemUTC());
        when(cache.currentRevision("news")).thenReturn(3L);
        when(cache.currentRevision("radar")).thenReturn(4L);

        assertEquals(Arrays.asList(3L, 4L), Arrays.asList(
                service.current(Arrays.asList("NEWS", "radar")).get(0).getRevision(),
                service.current(Arrays.asList("NEWS", "radar")).get(1).getRevision()));
    }
}
