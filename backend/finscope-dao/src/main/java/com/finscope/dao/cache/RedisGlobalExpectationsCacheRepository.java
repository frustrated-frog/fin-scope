package com.finscope.dao.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisGlobalExpectationsCacheRepository implements GlobalExpectationsCacheRepository {
    private static final Logger log = LoggerFactory.getLogger(RedisGlobalExpectationsCacheRepository.class);
    private static final String VIEW_KEY = "finscope:global-expectations:view";
    private static final String HISTORY_PREFIX = "finscope:global-expectations:history:";
    private static final String INTERPRETATION_PREFIX = "finscope:global-expectations:interpretation:";
    private static final long TTL_MS = TimeUnit.HOURS.toMillis(26L);

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Value("${finscope.redis.enabled:true}")
    private boolean enabled;

    @Override
    public Optional<GlobalExpectationHistorySnapshot> getHistory(String tokenId) {
        if (!enabled || tokenId == null || tokenId.isBlank()) {
            return Optional.empty();
        }
        return read(HISTORY_PREFIX + tokenId, GlobalExpectationHistorySnapshot.class);
    }

    @Override
    public void putHistory(GlobalExpectationHistorySnapshot snapshot) {
        if (!enabled || snapshot == null || snapshot.getTokenId() == null || snapshot.getTokenId().isBlank()) {
            return;
        }
        write(HISTORY_PREFIX + snapshot.getTokenId(), snapshot);
    }

    @Override
    public Optional<GlobalExpectationsViewSnapshot> getView() {
        if (!enabled) {
            return Optional.empty();
        }
        return read(VIEW_KEY, GlobalExpectationsViewSnapshot.class);
    }

    @Override
    public void putView(GlobalExpectationsViewSnapshot snapshot) {
        if (!enabled || snapshot == null || snapshot.getItems() == null) {
            return;
        }
        write(VIEW_KEY, snapshot);
    }

    @Override
    public Optional<GlobalExpectationInterpretation> getInterpretation(String groupId) {
        if (!enabled || groupId == null || groupId.isBlank()) {
            return Optional.empty();
        }
        return read(INTERPRETATION_PREFIX + groupId, GlobalExpectationInterpretation.class);
    }

    @Override
    public void putInterpretation(String groupId, GlobalExpectationInterpretation interpretation) {
        if (!enabled || groupId == null || groupId.isBlank() || interpretation == null) {
            return;
        }
        write(INTERPRETATION_PREFIX + groupId, interpretation);
    }

    private <T> Optional<T> read(String key, Class<T> valueType) {
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, valueType));
        } catch (Exception error) {
            log.warn("Redis 全球预期缓存读取失败: key={}, error={}", key, error.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, Object value) {
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            log.warn("Redis 全球预期缓存写入失败，不影响实时链路: key={}, error={}", key, error.getMessage());
        }
    }
}
