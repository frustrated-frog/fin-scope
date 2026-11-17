package com.finscope.dao.radar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarEventSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redis 协议辅助器，不是业务 Bean；每次只传输新增时间点和所需的一个历史时间点。 */
@Slf4j
final class RedisRadarSnapshotStorage {
    static final int MAX_BATCH_BYTES = 256 * 1024;
    private static final int MAX_POINT_BYTES = 64 * 1024;
    private static final int MAX_LEGACY_BYTES = 16 * 1024 * 1024;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS");
    private static final DefaultRedisScript<Long> APPEND = script("radar-snapshot-append.lua", Long.class);
    private static final DefaultRedisScript<List> READ = script("radar-snapshot-read.lua", List.class);
    private static final DefaultRedisScript<List> PENDING = script("radar-snapshot-pending.lua", List.class);
    private static final DefaultRedisScript<Long> MIGRATED = script("radar-snapshot-migrated.lua", Long.class);
    private final RedisRadarCacheStore store;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;

    RedisRadarSnapshotStorage(RedisRadarCacheStore store, StringRedisTemplate redis, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
    }

    void save(List<RadarEventSnapshot> snapshots) {
        // 先验证全部输入，避免非法单点导致前面的批次已经提交。
        for (RadarEventSnapshot snapshot : snapshots) {
            point(snapshot);
        }
        String prefix = prefix();
        migrate(prefix, snapshots.stream().map(RadarEventSnapshot::getEventId).distinct().toList());
        append(prefix, snapshots, false);
    }

    Map<Long, RadarEventSnapshot> latest(Collection<Long> eventIds, LocalDateTime before) {
        List<Long> ids = eventIds.stream().distinct().toList();
        String prefix = prefix();
        migrate(prefix, ids);
        Map<Long, RadarEventSnapshot> result = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, ids.size()));
            List<String> keys = new ArrayList<>();
            for (Long id : batch) {
                keys.add(key(prefix, "events", id));
                keys.add(key(prefix, "snapshotHistory", id));
            }
            List<?> rows = redis.execute(READ, keys, TIME.format(before), cutoff());
            if (rows == null || rows.size() != batch.size()) {
                throw new IllegalStateException("雷达历史快照批量读取未完成");
            }
            for (int index = 0; index < rows.size(); index++) {
                String payload = String.valueOf(rows.get(index));
                if (!payload.isEmpty()) {
                    try {
                        result.put(batch.get(index), mapper.readValue(payload, RadarEventSnapshot.class));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                        throw new IllegalStateException("雷达历史快照解析失败", error);
                    }
                }
            }
        }
        return result;
    }

    private void migrate(String prefix, List<Long> ids) {
        for (int start = 0; start < ids.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, ids.size()));
            List<String> keys = batch.stream().map(id -> key(prefix, "snapshots", id)).toList();
            List<?> pending = redis.execute(PENDING, keys);
            if (pending == null) {
                throw new IllegalStateException("雷达旧快照检查未完成");
            }
            for (Object position : pending) {
                int index = ((Number) position).intValue() - 1;
                String legacyKey = keys.get(index);
                Long size = redis.opsForValue().size(legacyKey);
                if (size != null && size > MAX_LEGACY_BYTES) {
                    throw new IllegalStateException("雷达旧快照超过单对象迁移上限，需要离线分批迁移");
                }
                String payload = redis.opsForValue().get(legacyKey);
                if (payload == null) {
                    continue;
                }
                try {
                    List<RadarEventSnapshot> old = mapper.readValue(payload, new TypeReference<>() { });
                    Long eventId = batch.get(index);
                    for (RadarEventSnapshot snapshot : old) {
                        if (!eventId.equals(snapshot.getEventId())) {
                            throw new IllegalStateException("雷达旧快照与父事件不匹配");
                        }
                    }
                    append(prefix, old.stream().filter(value -> value.getSnapshotAt() != null).toList(), true);
                    String fingerprint;
                    try {
                        fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1")
                                .digest(payload.getBytes(StandardCharsets.UTF_8)));
                    } catch (java.security.NoSuchAlgorithmException error) {
                        throw new IllegalStateException("当前 JDK 不支持快照迁移摘要", error);
                    }
                    redis.execute(MIGRATED, List.of(legacyKey, prefix + "snapshots:index"), fingerprint, member(eventId));
                } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                    throw new IllegalStateException("雷达旧快照迁移解析失败，保留原数据", error);
                }
            }
        }
    }

    private void append(String prefix, List<RadarEventSnapshot> snapshots, boolean migration) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>(List.of(cutoff(), migration ? "1" : "0"));
        int bytes = 128;
        for (RadarEventSnapshot snapshot : snapshots) {
            String value = point(snapshot);
            int size = value.getBytes(StandardCharsets.UTF_8).length
                    + key(prefix, "events", snapshot.getEventId()).length()
                    + key(prefix, "snapshotHistory", snapshot.getEventId()).length() + 64;
            if (!keys.isEmpty() && (keys.size() / 2 >= RadarCacheLayout.BATCH_SIZE || bytes + size > MAX_BATCH_BYTES)) {
                executeAppend(keys, args);
                keys = new ArrayList<>();
                args = new ArrayList<>(List.of(cutoff(), migration ? "1" : "0"));
                bytes = 128;
            }
            keys.add(key(prefix, "events", snapshot.getEventId()));
            keys.add(key(prefix, "snapshotHistory", snapshot.getEventId()));
            args.add(value);
            bytes += size;
        }
        if (!keys.isEmpty()) {
            executeAppend(keys, args);
        }
    }

    private void executeAppend(List<String> keys, List<String> args) {
        Long written = redis.execute(APPEND, keys, args.toArray());
        if (written == null) {
            throw new IllegalStateException("雷达历史快照提交未确认");
        }
        if (written < keys.size() / 2) {
            log.info("雷达历史快照跳过已过期的事件或时间点 skipped={}", keys.size() / 2 - written);
        }
    }

    private String point(RadarEventSnapshot snapshot) {
        if (snapshot == null || snapshot.getEventId() == null || snapshot.getSnapshotAt() == null) {
            throw new IllegalArgumentException("雷达快照必须包含事件和时间");
        }
        snapshot.setId(store.stableId("snapshot", snapshot.getEventId() + ":" + snapshot.getSnapshotAt()));
        try {
            String value = TIME.format(snapshot.getSnapshotAt()) + '|' + mapper.writeValueAsString(snapshot);
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_POINT_BYTES) {
                throw new IllegalArgumentException("雷达单个快照超过64KiB上限");
            }
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("雷达历史快照序列化失败", error);
        }
    }

    private String cutoff() {
        return TIME.format(LocalDateTime.now(clock).minusDays(RadarCacheLayout.SNAPSHOT_KEEP_DAYS));
    }

    private String prefix() {
        return RadarCacheLayout.prefix(((RedisRadarCacheSession) store.read()).generation());
    }

    private String key(String prefix, String section, Long id) {
        return prefix + section + ':' + member(id);
    }

    private String member(Long id) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }

    private static <T> DefaultRedisScript<T> script(String name, Class<T> type) {
        DefaultRedisScript<T> value = new DefaultRedisScript<>();
        value.setLocation(new ClassPathResource("redis/" + name));
        value.setResultType(type);
        return value;
    }
}
