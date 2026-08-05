package com.finscope.dao.cache;

import com.finscope.domain.research.material.ResearchMaterialCacheEntry;

import java.time.Duration;
import java.util.Optional;

public interface ResearchMaterialCacheRepository {
    Optional<ResearchMaterialCacheEntry> get(String key);

    void put(String key, ResearchMaterialCacheEntry value, Duration ttl);

    static ResearchMaterialCacheRepository noop() {
        return new ResearchMaterialCacheRepository() {
            @Override
            public Optional<ResearchMaterialCacheEntry> get(String key) {
                return Optional.empty();
            }

            @Override
            public void put(String key, ResearchMaterialCacheEntry value, Duration ttl) {
                // 本地单元测试或 Redis 未启用时保持原有无缓存行为。
            }
        };
    }
}
