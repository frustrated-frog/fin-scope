package com.finscope.dao.radar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.radar.RadarEventWorkspace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一次性、可重试的布局迁移。暂存对象全部有 TTL，最终只原子切换一个活动代号。 */
@Slf4j
final class RedisRadarCacheMigration {
    private static final int READ_CHUNK_BYTES = 256 * 1024;
    private static final long MAX_LEGACY_BYTES = 128L * 1024 * 1024;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final EphemeralContentCacheProperties properties;
    private final Clock clock;

    RedisRadarCacheMigration(StringRedisTemplate redis, ObjectMapper mapper,
                             EphemeralContentCacheProperties properties, Clock clock) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    String migrate() {
        for (int attempt = 0; attempt < 3; attempt++) {
            String result = redis.execute(new SessionCallback<String>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> String execute(RedisOperations<K, V> operations) {
                    RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                    ops.watch(List.of(RadarCacheLayout.ACTIVE_KEY, RadarCacheLayout.LEGACY_KEY));
                    try {
                        String active = ops.opsForValue().get(RadarCacheLayout.ACTIVE_KEY);
                        if (active != null) {
                            ops.unwatch();
                            return active;
                        }
                        return stageAndActivate(ops);
                    } catch (RuntimeException error) {
                        ops.unwatch();
                        throw error;
                    }
                }
            });
            if (result != null) {
                return result;
            }
        }
        throw new IllegalStateException("雷达旧缓存仍在变化，迁移未切换，请停止旧后端后重试");
    }

    private String stageAndActivate(RedisOperations<String, String> ops) {
        byte[] payload = readLegacy();
        RadarCacheState state = parse(payload);
        LocalDateTime now = LocalDateTime.now(clock);
        int ttlHours = Math.max(1, properties.getTtlHours());
        RadarCachePruner.prune(state, now, ttlHours, clock.getZone());
        String generation = UUID.randomUUID().toString();
        String prefix = RadarCacheLayout.prefix(generation);
        int records = stage(state, prefix, now, ttlHours);
        Long legacyTtl = ops.getExpire(RadarCacheLayout.LEGACY_KEY);
        ops.multi();
        ops.opsForValue().set(prefix + "revision", "0");
        ops.opsForValue().set(prefix + "sequence", String.valueOf(Math.max(0L, state.getNextSequence() - 1L)));
        ops.opsForValue().set(RadarCacheLayout.ACTIVE_KEY, generation);
        if (Long.valueOf(-1L).equals(legacyTtl)) {
            ops.expire(RadarCacheLayout.LEGACY_KEY, java.time.Duration.ofHours(ttlHours));
        }
        List<Object> committed = ops.exec();
        if (committed == null || committed.isEmpty()) {
            return null;
        }
        log.info("雷达缓存布局迁移完成 legacyBytes={} records={} signals={} events={}，旧键保留原 TTL",
                payload.length, records, state.getSignals().size(), state.getEvents().size());
        return generation;
    }

    private byte[] readLegacy() {
        return redis.execute((RedisCallback<byte[]>) connection -> {
            byte[] key = RadarCacheLayout.LEGACY_KEY.getBytes(StandardCharsets.UTF_8);
            Long length = connection.stringCommands().strLen(key);
            if (length == null || length == 0L) {
                return new byte[0];
            }
            if (length > MAX_LEGACY_BYTES) {
                throw new IllegalStateException("雷达旧缓存超过迁移读取上限，未改动原数据");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(length.intValue());
            for (long start = 0; start < length; start += READ_CHUNK_BYTES) {
                byte[] chunk = connection.stringCommands().getRange(key, start,
                        Math.min(length - 1L, start + READ_CHUNK_BYTES - 1L));
                if (chunk == null) {
                    throw new IllegalStateException("雷达旧缓存分段读取失败，未切换布局");
                }
                bytes.writeBytes(chunk);
            }
            // 按字节拼接后再解码，中文 UTF-8 字符跨分段边界也不会损坏。
            return bytes.toByteArray();
        });
    }

    private RadarCacheState parse(byte[] payload) {
        if (payload.length == 0) {
            return new RadarCacheState();
        }
        try {
            return mapper.readerFor(RadarCacheState.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("雷达旧缓存解析失败，未切换布局", error);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("雷达旧缓存读取失败，未切换布局", error);
        }
    }

    private int stage(RadarCacheState state, String prefix, LocalDateTime now, int ttlHours) {
        int count = 0;
        for (RadarCacheLayout.Section section : RadarCacheLayout.sections(mapper)) {
            count += stageEntries(section, section.source().apply(state), state, prefix, now, ttlHours);
        }
        Map<Long, RadarEventWorkspace.Notification> notifications = new java.util.LinkedHashMap<>();
        for (RadarEventWorkspace.Notification notification : state.getNotifications()) {
            notifications.put(notification.getId(), notification);
        }
        count += stageEntries(RadarCacheLayout.notifications(mapper), notifications, state, prefix, now, ttlHours);
        Map<String, String> fingerprints = new java.util.LinkedHashMap<>();
        for (String fingerprint : state.getNotificationFingerprints()) {
            fingerprints.put(fingerprint, "1");
        }
        count += stageEntries(RadarCacheLayout.notificationFingerprints(mapper), fingerprints, state, prefix, now, ttlHours);
        return count;
    }

    private int stageEntries(RadarCacheLayout.Section section, Map<?, ?> entries, RadarCacheState state,
                              String prefix, LocalDateTime now, int ttlHours) {
        List<StagedRecord> batch = new ArrayList<>();
        int count = 0;
        long indexDeadline = 0L;
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            long deadline = RadarCacheExpiry.deadline(section.name(), entry.getKey(), entry.getValue(),
                    state, now, ttlHours, clock.getZone());
            if (deadline <= clock.millis()) {
                continue;
            }
            String json;
            try {
                json = mapper.writeValueAsString(entry.getValue());
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("雷达迁移记录序列化失败: " + section.name(), error);
            }
            batch.add(new StagedRecord(section.key(prefix, entry.getKey()), section.member(entry.getKey()), json, deadline));
            indexDeadline = Math.max(indexDeadline, deadline);
            count++;
            if (batch.size() >= RadarCacheLayout.BATCH_SIZE) {
                writeBatch(section.index(prefix), batch, indexDeadline);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            writeBatch(section.index(prefix), batch, indexDeadline);
        }
        return count;
    }

    private void writeBatch(String index, List<StagedRecord> records, long indexDeadline) {
        redis.executePipelined((RedisCallback<Object>) connection -> {
            byte[] indexKey = index.getBytes(StandardCharsets.UTF_8);
            for (StagedRecord record : records) {
                long remaining = record.deadline() - clock.millis();
                if (remaining <= 0L) {
                    continue;
                }
                connection.stringCommands().set(record.key().getBytes(StandardCharsets.UTF_8),
                        record.payload().getBytes(StandardCharsets.UTF_8), Expiration.milliseconds(remaining),
                        RedisStringCommands.SetOption.UPSERT);
                connection.zSetCommands().zAdd(indexKey, record.deadline(), record.member().getBytes(StandardCharsets.UTF_8));
            }
            connection.keyCommands().pExpireAt(indexKey, indexDeadline);
            return null;
        });
    }

    private record StagedRecord(String key, String member, String payload, long deadline) {
    }
}
