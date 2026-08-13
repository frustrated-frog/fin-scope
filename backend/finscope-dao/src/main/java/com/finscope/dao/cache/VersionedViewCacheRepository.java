package com.finscope.dao.cache;

import java.time.Duration;
import java.util.Map;
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

    /** 返回尚未对读取方可见的下一版本号，用于预写完整页面快照。 */
    long nextRevision(String namespace);

    /** 将快照写入指定版本；只有返回 true 的内容才可以随后激活。 */
    boolean put(String namespace, long revision, String variant, String payload, Duration ttl);

    /** 激活已完整写入的版本，后续读取才会切换到该版本。 */
    boolean activateRevision(String namespace, long revision);

    /** 在一个 Redis 原子操作中同时激活多个页面范围，避免同批页面版本撕裂。 */
    boolean activateRevisions(Map<String, Long> revisions);

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
            public long nextRevision(String namespace) {
                return 0L;
            }

            @Override
            public boolean put(String namespace, long revision, String variant, String payload, Duration ttl) {
                return false;
            }

            @Override
            public boolean activateRevision(String namespace, long revision) {
                return false;
            }

            @Override
            public boolean activateRevisions(Map<String, Long> revisions) {
                return false;
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
