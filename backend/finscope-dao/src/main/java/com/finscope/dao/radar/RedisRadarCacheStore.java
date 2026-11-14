package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 按对象读取、按变更提交的雷达临时存储。旧整包状态只在首次迁移时读取。 */
@Component
@Slf4j
public class RedisRadarCacheStore {
    private static final int MAX_ATTEMPTS = 4;
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = new DefaultRedisScript<>();

    static {
        COMMIT_SCRIPT.setLocation(new ClassPathResource("redis/radar-commit.lua"));
        COMMIT_SCRIPT.setResultType(Long.class);
    }

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private EphemeralContentCacheProperties properties;
    private Clock clock = Clock.systemDefaultZone();

    public RadarCacheState read() {
        String generation = ensureReady();
        return new RedisRadarCacheSession(this, generation, revision(generation), LocalDateTime.now(clock));
    }

    /** 多记录详情在版本稳定后返回；遇到并发提交时重新读取，不持有生产任务的锁。 */
    public <T> T query(Function<RadarCacheState, T> query) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            RadarCacheState state = read();
            T result = query.apply(state);
            if (!(state instanceof RedisRadarCacheSession session)
                    || session.revision().equals(revision(session.generation()))) {
                return result;
            }
        }
        throw new IllegalStateException("雷达缓存正在并发更新，请重试读取");
    }

    public synchronized <T> T update(Function<RadarCacheState, T> mutation) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            RedisRadarCacheSession session = (RedisRadarCacheSession) read();
            T result = mutation.apply(session);
            List<RedisRadarCacheSession.Change> changes = session.changes();
            if (changes.isEmpty() ? session.revision().equals(revision(session.generation())) : commit(session, changes)) {
                return result;
            }
        }
        throw new IllegalStateException("雷达缓存并发更新冲突，请重试写入");
    }

    private boolean commit(RedisRadarCacheSession session, List<RedisRadarCacheSession.Change> changes) {
        String prefix = RadarCacheLayout.prefix(session.generation());
        String revisionKey = prefix + "revision";
        List<String> keys = new ArrayList<>(List.of(revisionKey));
        List<String> args = new ArrayList<>(List.of(session.revision(), String.valueOf(clock.millis())));
        long bytes = 0L;
        for (RedisRadarCacheSession.Change change : changes) {
            keys.add(change.section().key(prefix, change.id()));
            keys.add(change.section().index(prefix));
            keys.add(change.parentKey().isEmpty() ? revisionKey : change.parentKey());
            args.add(change.section().member(change.id()));
            args.add(change.payload() == null ? "" : change.payload());
            args.add(String.valueOf(change.deadline()));
            args.add(change.parentKey().isEmpty() ? "0" : "1");
            bytes += change.payload() == null ? 0L : change.payload().getBytes(StandardCharsets.UTF_8).length;
        }
        long started = System.nanoTime();
        Long committed = redisTemplate.execute(COMMIT_SCRIPT, keys, args.toArray());
        if (Long.valueOf(-1L).equals(committed)) {
            throw new IllegalStateException("雷达父记录不存在或已过期，拒绝写入悬空关联");
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        if (elapsedMs >= 250L || bytes >= 1_048_576L) {
            log.info("雷达缓存增量提交 records={} bytes={} durationMs={} committed={}",
                    changes.size(), bytes, elapsedMs, committed);
        }
        return Long.valueOf(1L).equals(committed);
    }

    private String ensureReady() {
        String active = redisTemplate.opsForValue().get(RadarCacheLayout.ACTIVE_KEY);
        if (active != null) {
            return active;
        }
        return migrate();
    }

    private synchronized String migrate() {
        String active = redisTemplate.opsForValue().get(RadarCacheLayout.ACTIVE_KEY);
        if (active != null) {
            return active;
        }
        // 协议迁移辅助器不是 Spring Bean，不承载业务编排。
        return new RedisRadarCacheMigration(redisTemplate, objectMapper, properties, clock).migrate();
    }

    private String revision(String generation) {
        String value = redisTemplate.opsForValue().get(RadarCacheLayout.prefix(generation) + "revision");
        return value == null ? "0" : value;
    }

    List<String> readRecords(String generation, RadarCacheLayout.Section section, Collection<?> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = ids.stream().map(id -> section.key(RadarCacheLayout.prefix(generation), id)).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.size() != keys.size()) {
            throw new IllegalStateException("Redis 雷达记录批量读取未完成: " + section.name());
        }
        return values;
    }

    Set<String> members(String generation, RadarCacheLayout.Section section, LocalDateTime now) {
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(section.index(RadarCacheLayout.prefix(generation)),
                epochMillis(now) + 1D, Double.POSITIVE_INFINITY);
        return members == null ? Collections.emptySet() : members;
    }

    long nextSequence(String generation) {
        Long value = redisTemplate.opsForValue().increment(RadarCacheLayout.prefix(generation) + "sequence");
        if (value == null) {
            throw new IllegalStateException("Redis 雷达序号分配失败");
        }
        return value;
    }

    ObjectMapper mapper() {
        return objectMapper;
    }

    int ttlHours() {
        return Math.max(1, properties.getTtlHours());
    }

    java.time.ZoneId zone() {
        return clock.getZone();
    }

    long epochMillis(LocalDateTime time) {
        return time.atZone(clock.getZone()).toInstant().toEpochMilli();
    }

    public long stableId(String namespace, String businessKey) {
        if (namespace == null || namespace.trim().isEmpty() || businessKey == null || businessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("缓存标识的命名空间和业务键不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (namespace.trim() + ':' + businessKey.trim()).getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                value = (value << 8) | (digest[index] & 0xffL);
            }
            value &= Long.MAX_VALUE;
            return value == 0L ? 1L : value;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", error);
        }
    }

    public Duration remainingTtl(LocalDateTime baseTime, LocalDateTime now) {
        if (baseTime == null || now == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(now, baseTime.plusHours(ttlHours()));
        return remaining.isNegative() || remaining.isZero() ? Duration.ZERO : remaining;
    }

    public static <K, V> void prefetch(Map<K, V> records, Collection<K> ids) {
        RedisRadarCacheSession.prefetch(records, ids);
    }
}
