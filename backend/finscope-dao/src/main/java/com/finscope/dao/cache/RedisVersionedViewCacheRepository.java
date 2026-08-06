package com.finscope.dao.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Redis 实现的页面快照版本缓存。 */
@Component
public class RedisVersionedViewCacheRepository implements VersionedViewCacheRepository {
    private static final Logger log = LoggerFactory.getLogger(RedisVersionedViewCacheRepository.class);
    private static final String PREFIX = "finscope:view:";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    @Autowired
    public RedisVersionedViewCacheRepository(StringRedisTemplate redisTemplate,
                                             @Value("${finscope.redis.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    public RedisVersionedViewCacheRepository(StringRedisTemplate redisTemplate) {
        this(redisTemplate, true);
    }

    @Override
    public Optional<String> get(String namespace, String variant) {
        if (!enabled || invalid(namespace) || invalid(variant)) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(snapshotKey(namespace, variant));
            return value == null || value.trim().isEmpty() ? Optional.empty() : Optional.of(value);
        } catch (RuntimeException error) {
            log.warn("Redis 页面快照缓存读取失败，回退主链路: namespace={}, error={}", namespace, error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String namespace, String variant, String payload, Duration ttl) {
        put(namespace, currentRevision(namespace), variant, payload, ttl);
    }

    @Override
    public long nextRevision(String namespace) {
        if (!enabled || invalid(namespace)) {
            return 0L;
        }
        try {
            return currentRevision(namespace) + 1L;
        } catch (RuntimeException error) {
            log.warn("Redis 页面快照下一版本读取失败: namespace={}, error={}", namespace, error.getMessage());
            return 0L;
        }
    }

    @Override
    public boolean put(String namespace, long revision, String variant, String payload, Duration ttl) {
        if (!enabled || invalid(namespace) || invalid(variant) || payload == null || payload.trim().isEmpty()
                || revision < 0 || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(snapshotKey(namespace, revision, variant), payload,
                    ttl.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (RuntimeException error) {
            log.warn("Redis 页面快照缓存写入失败，不影响主链路: namespace={}, error={}", namespace, error.getMessage());
            return false;
        }
    }

    @Override
    public void activateRevision(String namespace, long revision) {
        if (!enabled || invalid(namespace) || revision < 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(revisionKey(namespace), String.valueOf(revision));
        } catch (RuntimeException error) {
            log.warn("Redis 页面快照版本发布失败，保留上一版本: namespace={}, error={}", namespace, error.getMessage());
        }
    }

    @Override
    public long currentRevision(String namespace) {
        if (!enabled || invalid(namespace)) {
            return 0L;
        }
        try {
            return revision(namespace);
        } catch (RuntimeException error) {
            log.warn("Redis 页面快照 revision 读取失败，将使用初始版本: namespace={}, error={}", namespace, error.getMessage());
            return 0L;
        }
    }

    @Override
    public long invalidateAndGetRevision(String namespace) {
        if (!enabled || invalid(namespace)) {
            return 0L;
        }
        try {
            Long revision = redisTemplate.opsForValue().increment(revisionKey(namespace));
            return revision == null ? 0L : revision;
        } catch (RuntimeException error) {
            log.warn("Redis 页面快照缓存失效失败，缓存将随 TTL 自然过期: namespace={}, error={}", namespace, error.getMessage());
            return 0L;
        }
    }

    private String snapshotKey(String namespace, String variant) {
        return snapshotKey(namespace, revision(namespace), variant);
    }

    private String snapshotKey(String namespace, long revision, String variant) {
        return PREFIX + normalize(namespace) + ':' + revision + ':' + variant.trim();
    }

    private long revision(String namespace) {
        String value = redisTemplate.opsForValue().get(revisionKey(namespace));
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException error) {
            log.warn("Redis 页面快照 revision 非法，将使用初始版本: namespace={}", namespace);
            return 0L;
        }
    }

    private String revisionKey(String namespace) {
        return PREFIX + normalize(namespace) + ":revision";
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean invalid(String value) {
        return value == null || value.trim().isEmpty();
    }
}
