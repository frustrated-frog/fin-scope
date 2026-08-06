package com.finscope.dao.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisVersionedViewCacheRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void readsAndWritesPayloadAtCurrentRevision() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("finscope:view:radar:revision")).thenReturn("7");
        when(valueOperations.get("finscope:view:radar:7:category=ALL")).thenReturn("{\"events\":[]}");

        RedisVersionedViewCacheRepository repository = new RedisVersionedViewCacheRepository(redisTemplate);

        Optional<String> cached = repository.get("radar", "category=ALL");
        repository.put("radar", "category=ALL", "{\"events\":[]}", Duration.ofSeconds(60));

        assertTrue(cached.isPresent());
        assertEquals("{\"events\":[]}", cached.get());
        verify(valueOperations).set(eq("finscope:view:radar:7:category=ALL"),
                eq("{\"events\":[]}"), eq(60000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void invalidationMovesReadsToNextRevision() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("finscope:view:radar:revision")).thenReturn(8L);
        when(valueOperations.get("finscope:view:radar:revision")).thenReturn("8");

        RedisVersionedViewCacheRepository repository = new RedisVersionedViewCacheRepository(redisTemplate);

        long revision = repository.invalidateAndGetRevision("radar");

        assertEquals(8L, revision);
        assertFalse(repository.get("radar", "category=ALL").isPresent());
        verify(valueOperations).get("finscope:view:radar:8:category=ALL");
    }

    @Test
    void exposesCurrentRevisionWithoutChangingIt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("finscope:view:dashboard:revision")).thenReturn("12");

        RedisVersionedViewCacheRepository repository = new RedisVersionedViewCacheRepository(redisTemplate);

        assertEquals(12L, repository.currentRevision("dashboard"));
    }

    @Test
    void redisFailureFallsBackToEmptyCache() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        RedisVersionedViewCacheRepository repository = new RedisVersionedViewCacheRepository(redisTemplate);

        assertFalse(repository.get("radar", "category=ALL").isPresent());
        assertEquals(0L, repository.invalidateAndGetRevision("radar"));
    }
}
