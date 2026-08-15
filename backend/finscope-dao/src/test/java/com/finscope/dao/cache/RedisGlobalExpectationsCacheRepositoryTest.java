package com.finscope.dao.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.globalexpectations.GlobalExpectationHistoryPoint;
import com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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
class RedisGlobalExpectationsCacheRepositoryTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RedisGlobalExpectationsCacheRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new RedisGlobalExpectationsCacheRepository();
        ReflectionTestUtils.setField(repository, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(repository, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(repository, "enabled", true);
    }

    @Test
    void writesHistoryAndViewWithTwentySixHourTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.putHistory(history());
        repository.putView(view());
        repository.putInterpretation("event:fed", interpretation());

        verify(valueOperations).set(eq("finscope:global-expectations:history:yes-token"),
                anyString(), eq(93600000L), eq(TimeUnit.MILLISECONDS));
        verify(valueOperations).set(eq("finscope:global-expectations:view"),
                anyString(), eq(93600000L), eq(TimeUnit.MILLISECONDS));
        verify(valueOperations).set(eq("finscope:global-expectations:interpretation:event:fed"),
                anyString(), eq(93600000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void readsHistoryAndViewJson() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("finscope:global-expectations:history:yes-token"))
                .thenReturn(objectMapper.writeValueAsString(history()));
        when(valueOperations.get("finscope:global-expectations:view"))
                .thenReturn(objectMapper.writeValueAsString(view()));
        when(valueOperations.get("finscope:global-expectations:interpretation:event:fed"))
                .thenReturn(objectMapper.writeValueAsString(interpretation()));

        Optional<GlobalExpectationHistorySnapshot> history = repository.getHistory("yes-token");
        Optional<GlobalExpectationsViewSnapshot> view = repository.getView();
        Optional<GlobalExpectationInterpretation> interpretation = repository.getInterpretation("event:fed");

        assertTrue(history.isPresent());
        assertEquals(27.0D, history.get().getPoints().get(0).getProbability());
        assertTrue(view.isPresent());
        assertEquals(31, view.get().getItems().get(0).getProbability());
        assertEquals("READY", interpretation.orElseThrow().getStatus());
    }

    @Test
    void malformedOrUnavailableRedisReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("finscope:global-expectations:view")).thenReturn("not-json");

        assertFalse(repository.getView().isPresent());
        assertFalse(repository.getHistory("").isPresent());
    }

    private GlobalExpectationHistorySnapshot history() {
        GlobalExpectationHistoryPoint point = new GlobalExpectationHistoryPoint();
        point.setTimestamp(1786748100L);
        point.setProbability(27.0D);
        GlobalExpectationHistorySnapshot snapshot = new GlobalExpectationHistorySnapshot();
        snapshot.setTokenId("yes-token");
        snapshot.setFetchedAt(1786748400L);
        snapshot.setPoints(List.of(point));
        return snapshot;
    }

    private GlobalExpectationsViewSnapshot view() {
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setProbability(31);
        GlobalExpectationsViewSnapshot snapshot = new GlobalExpectationsViewSnapshot();
        snapshot.setFetchedAt(1786748400L);
        snapshot.setItems(List.of(item));
        return snapshot;
    }

    private GlobalExpectationInterpretation interpretation() {
        GlobalExpectationInterpretation interpretation = new GlobalExpectationInterpretation();
        interpretation.setStatus("READY");
        interpretation.setHappened("概率快速上升");
        return interpretation;
    }
}
