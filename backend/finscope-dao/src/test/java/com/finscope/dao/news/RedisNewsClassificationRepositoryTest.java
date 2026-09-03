package com.finscope.dao.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.news.NewsItemClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisNewsClassificationRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> values = new HashMap<String, String>();
    private NewsClassificationRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
        EphemeralContentCacheProperties properties = new EphemeralContentCacheProperties();
        properties.setTtlHours(36);
        repository = new NewsClassificationRepository();
        ReflectionTestUtils.setField(repository, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(repository, "properties", properties);
    }

    @Test
    void classifiesAndReviewsWithoutDatabaseState() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);

        assertTrue(repository.claim("CLS:1", now, now.minusMinutes(5)));
        assertFalse(repository.claim("CLS:1", now.plusSeconds(1), now.minusMinutes(5)));
        repository.markClassified("CLS:1", "COMPANY", 0.65, "公司公告", "model-a", now.plusHours(1));
        assertTrue(repository.review("CLS:1", "INDUSTRY", "产业链影响", now.plusHours(2)));

        NewsItemClassification value = repository.findByItemIds(Arrays.asList("CLS:1", "MISSING:2")).get("CLS:1");
        assertEquals("COMPANY", value.getCategoryCode());
        assertEquals("INDUSTRY", value.getEffectiveCategoryCode());
        assertEquals("CORRECTED", value.getReviewStatus());
        assertEquals(now, value.getCreatedAt());
        verify(valueOperations).set(anyString(), anyString(), eq(TimeUnit.HOURS.toMillis(35)), eq(TimeUnit.MILLISECONDS));
        verify(valueOperations).set(anyString(), anyString(), eq(TimeUnit.HOURS.toMillis(34)), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void retriesFailedClassificationOnlyAfterBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        assertTrue(repository.claim("THS:2", now, now.minusMinutes(5)));
        repository.markFailed("THS:2", "模型不可用", "model-a", now);

        assertFalse(repository.claim("THS:2", now.plusMinutes(4), now.minusMinutes(1)));
        assertTrue(repository.claim("THS:2", now.plusMinutes(6), now.plusMinutes(1)));
        assertEquals("PENDING", repository.findByItemIds(Arrays.asList("THS:2")).get("THS:2").getStatus());
    }
}
