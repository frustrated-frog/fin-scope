package com.finscope.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.VersionedViewCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewSnapshotCacheServiceTest {
    @Test
    void returnsCachedPayloadWithoutRunningThePageLoader() {
        VersionedViewCacheRepository cache = mock(VersionedViewCacheRepository.class);
        when(cache.get("news", "category=ALL&limit=100")).thenReturn(java.util.Optional.of("{\"items\":[]}"));
        AtomicInteger loaded = new AtomicInteger();
        ViewSnapshotCacheService service = new ViewSnapshotCacheService(cache, new ObjectMapper());

        assertEquals(0, service.readOrLoad("news", "category=ALL&limit=100", Duration.ofSeconds(30),
                () -> { loaded.incrementAndGet(); return Collections.singletonMap("items", "unexpected"); }).get("items").size());
        assertEquals(0, loaded.get());
    }

    @Test
    void serializesAndStoresAColdPageSnapshot() {
        VersionedViewCacheRepository cache = mock(VersionedViewCacheRepository.class);
        when(cache.get("dashboard", "summary")).thenReturn(java.util.Optional.empty());
        ViewSnapshotCacheService service = new ViewSnapshotCacheService(cache, new ObjectMapper());

        assertEquals(20, service.readOrLoad("dashboard", "summary", Duration.ofSeconds(30),
                () -> Collections.singletonMap("articleCount", 20)).get("articleCount").asInt());
        verify(cache).put("dashboard", "summary", "{\"articleCount\":20}", Duration.ofSeconds(30));
    }
}
