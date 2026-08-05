package com.finscope.dao.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterialCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 研究资料查询的 Redis 加速层。
 *
 * <p>Redis 只负责降低重复抓取和聚合查询的延迟，任何连接、序列化或数据损坏
 * 都会退回到现有的实时抓取链路，不改变 SQLite 主链路的可用性。</p>
 */
@Component
public class RedisResearchMaterialCacheRepository implements ResearchMaterialCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisResearchMaterialCacheRepository.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    @Autowired
    public RedisResearchMaterialCacheRepository(StringRedisTemplate redisTemplate,
                                                ObjectMapper objectMapper,
                                                @Value("${finscope.redis.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public RedisResearchMaterialCacheRepository(StringRedisTemplate redisTemplate,
                                                ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, true);
    }

    @Override
    public Optional<ResearchMaterialCacheEntry> get(String key) {
        if (!enabled || key == null || key.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.trim().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, ResearchMaterialCacheEntry.class));
        } catch (Exception ex) {
            log.warn("Redis 研究资料缓存读取失败，回退实时链路: key={}, error={}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, ResearchMaterialCacheEntry value, Duration ttl) {
        if (!enabled || key == null || key.trim().isEmpty() || value == null
                || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("Redis 研究资料缓存写入失败，不影响实时链路: key={}, error={}", key, ex.getMessage());
        }
    }
}
