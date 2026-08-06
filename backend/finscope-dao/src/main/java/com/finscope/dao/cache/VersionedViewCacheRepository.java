package com.finscope.dao.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * 面向只读页面快照的版本化缓存。
 *
 * <p>每次失效只递增命名空间 revision，旧快照交由 TTL 自动回收，避免批量扫描 Redis
 * 删除 key。读取、写入和失效失败都必须由调用方的 SQLite 主链路兜底。</p>
 */
public interface VersionedViewCacheRepository {
    Optional<String> get(String namespace, String variant);

    void put(String namespace, String variant, String payload, Duration ttl);

    long currentRevision(String namespace);

    long invalidateAndGetRevision(String namespace);

    static VersionedViewCacheRepository noop() {
        return new VersionedViewCacheRepository() {
            @Override
            public Optional<String> get(String namespace, String variant) {
                return Optional.empty();
            }

            @Override
            public void put(String namespace, String variant, String payload, Duration ttl) {
                // Redis 未启用时保留既有读取路径。
            }

            @Override
            public long currentRevision(String namespace) {
                return 0L;
            }

            @Override
            public long invalidateAndGetRevision(String namespace) {
                return 0L;
            }
        };
    }
}
