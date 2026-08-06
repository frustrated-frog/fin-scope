package com.finscope.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.VersionedViewCacheRepository;
import com.finscope.service.news.NewsFeedSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/** 缓存页面 data JSON；API 外层的 traceId/timestamp 每次请求仍由 Controller 新建。 */
@Service
public class ViewSnapshotCacheService {
    private static final Logger log = LoggerFactory.getLogger(ViewSnapshotCacheService.class);
    private final VersionedViewCacheRepository cache;
    private final ObjectMapper mapper;

    public ViewSnapshotCacheService(VersionedViewCacheRepository cache, ObjectMapper mapper) {
        this.cache = cache;
        this.mapper = mapper;
    }

    public JsonNode readOrLoad(String scope, String variant, Duration ttl, Supplier<?> loader) {
        Optional<JsonNode> cached = read(scope, variant);
        if (cached.isPresent()) return cached.get();
        Object value = loader.get();
        try {
            String payload = mapper.writeValueAsString(value);
            if (shouldCache(scope, value)) cache.put(scope, variant, payload, ttl);
            return mapper.readTree(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("页面快照序列化失败", error);
        }
    }

    /** 只读预热快照；页面读取方不得在未命中时隐式访问主存储。 */
    public Optional<JsonNode> read(String scope, String variant) {
        Optional<String> cached = cache.get(scope, variant);
        if (!cached.isPresent()) return Optional.empty();
        try {
            return Optional.of(mapper.readTree(cached.get()));
        } catch (JsonProcessingException error) {
            log.warn("页面快照缓存格式无效: scope={}", scope);
            return Optional.empty();
        }
    }

    public long nextRevision(String scope) {
        return cache.nextRevision(scope);
    }

    /** 将 JSON 写入尚不可见的版本，调用方负责在全部写完后发布 revision。 */
    public boolean write(String scope, long revision, String variant, Object value, Duration ttl) {
        try {
            return cache.put(scope, revision, variant, mapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException error) {
            log.warn("页面快照序列化失败: scope={}", scope, error);
            return false;
        }
    }

    private boolean shouldCache(String scope, Object value) {
        return !("news".equalsIgnoreCase(scope) && value instanceof NewsFeedSnapshot
                && ((NewsFeedSnapshot) value).getItems().isEmpty());
    }
}
