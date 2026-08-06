package com.finscope.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.cache.VersionedViewCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
        java.util.Optional<String> cached = cache.get(scope, variant);
        if (cached.isPresent()) {
            try {
                return mapper.readTree(cached.get());
            } catch (JsonProcessingException error) {
                log.warn("页面快照缓存格式无效，将重新加载: scope={}", scope);
            }
        }
        Object value = loader.get();
        try {
            String payload = mapper.writeValueAsString(value);
            cache.put(scope, variant, payload, ttl);
            return mapper.readTree(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("页面快照序列化失败", error);
        }
    }
}
