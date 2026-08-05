package com.finscope.dao.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialCacheEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisResearchMaterialCacheRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void readsCachedEntryFromRedis() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        ResearchMaterialCacheEntry entry = entry("cached", "缓存资讯");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache-key")).thenReturn(objectMapper.writeValueAsString(entry));

        RedisResearchMaterialCacheRepository repository = repository(objectMapper);

        Optional<ResearchMaterialCacheEntry> result = repository.get("cache-key");

        assertTrue(result.isPresent());
        assertEquals("cached", result.get().getMaterials().get(0).getExternalId());
        assertEquals("缓存资讯", result.get().getMaterials().get(0).getTitle());
    }

    @Test
    void writesCachedEntryWithTtl() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisResearchMaterialCacheRepository repository = repository(objectMapper);
        repository.put("cache-key", entry("new", "新资讯"), Duration.ofSeconds(120));

        verify(valueOperations).set(eq("cache-key"), anyString(), eq(120000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void ignoresMalformedPayload() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache-key")).thenReturn("not-json");

        RedisResearchMaterialCacheRepository repository = repository(objectMapper());

        assertFalse(repository.get("cache-key").isPresent());
    }

    private RedisResearchMaterialCacheRepository repository(ObjectMapper objectMapper) {
        return new RedisResearchMaterialCacheRepository(redisTemplate, objectMapper);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private ResearchMaterialCacheEntry entry(String id, String title) {
        ResearchMaterial material = new ResearchMaterial();
        material.setExternalId(id);
        material.setTitle(title);
        return new ResearchMaterialCacheEntry(
                Collections.singletonList(material),
                Collections.<String>emptyList(),
                LocalDateTime.of(2026, 8, 5, 20, 0));
    }
}
