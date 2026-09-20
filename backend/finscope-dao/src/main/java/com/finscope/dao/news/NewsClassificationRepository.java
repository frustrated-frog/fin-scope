package com.finscope.dao.news;

import com.finscope.common.enums.news.NewsClassificationStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.domain.news.NewsItemClassification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
public class NewsClassificationRepository {
    private static final String KEY_PREFIX = "finscope:news:classification:";

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private EphemeralContentCacheProperties properties;

    public synchronized boolean claim(String itemId, LocalDateTime now, LocalDateTime retryBefore) {
        NewsItemClassification existing = read(itemId);
        if (existing == null) {
            write(pending(itemId, now, now), now);
            return true;
        }
        if ((!NewsClassificationStatus.FAILED.name().equals(existing.getStatus()) && !NewsClassificationStatus.PENDING.name().equals(existing.getStatus())) || existing.getUpdatedAt() == null
                || existing.getUpdatedAt().isAfter(retryBefore)) {
            return false;
        }
        write(pending(itemId, existing.getCreatedAt(), now), now);
        return true;
    }

    public synchronized void markClassified(String itemId, String categoryCode, double confidence, String reason,
                                             String modelName, LocalDateTime now) {
        NewsItemClassification existing = read(itemId);
        if (existing == null) {
            return;
        }
        existing.setStatus(NewsClassificationStatus.CLASSIFIED.name());
        existing.setCategoryCode(categoryCode);
        existing.setConfidence(confidence);
        existing.setReason(reason);
        existing.setModelName(modelName);
        existing.setErrorMessage(null);
        if (existing.getManualCategoryCode() == null) {
            existing.setReviewStatus(confidence < 0.70 ? "PENDING_REVIEW" : "AUTO_CONFIRMED");
        }
        existing.setUpdatedAt(now);
        write(existing, now);
    }

    public synchronized void markRuleResult(NewsItemClassification result, LocalDateTime now) {
        NewsItemClassification existing = read(result.getItemId());
        if (existing == null || existing.isManuallyReviewed()) {
            return;
        }
        existing.setStatus(result.getStatus());
        existing.setCategoryCode(result.getCategoryCode());
        existing.setConfidence(0);
        existing.setReason(result.getReason());
        existing.setModelName(result.getModelName());
        existing.setReviewStatus(NewsClassificationStatus.UNCLASSIFIED.name().equals(result.getStatus()) ? null : "PENDING_REVIEW");
        existing.setErrorMessage(null);
        existing.setUpdatedAt(now);
        write(existing, now);
    }

    public synchronized void markFailed(String itemId, String errorMessage, String modelName, LocalDateTime now) {
        NewsItemClassification existing = read(itemId);
        if (existing == null) {
            return;
        }
        existing.setStatus(NewsClassificationStatus.FAILED.name());
        existing.setCategoryCode(null);
        existing.setConfidence(0);
        existing.setReason(null);
        existing.setModelName(modelName);
        existing.setErrorMessage(errorMessage);
        existing.setUpdatedAt(now);
        write(existing, now);
    }

    public synchronized boolean review(String itemId, String categoryCode, String reason, LocalDateTime now) {
        NewsItemClassification existing = read(itemId);
        if (existing == null || !NewsClassificationStatus.CLASSIFIED.name().equals(existing.getStatus())) {
            return false;
        }
        existing.setManualCategoryCode(categoryCode);
        existing.setManualReason(reason);
        existing.setReviewStatus(categoryCode.equals(existing.getCategoryCode()) ? "CONFIRMED" : "CORRECTED");
        existing.setReviewedAt(now);
        existing.setUpdatedAt(now);
        write(existing, now);
        return true;
    }

    public synchronized Map<String, NewsItemClassification> findByItemIds(Collection<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, NewsItemClassification> result = new LinkedHashMap<String, NewsItemClassification>();
        java.util.List<String> ids = new java.util.ArrayList<>(itemIds);
        for (int start = 0; start < ids.size(); start += 500) {
            java.util.List<String> batch = ids.subList(start, Math.min(start + 500, ids.size()));
            java.util.List<String> keys = batch.stream().map(this::key)
                    .collect(java.util.stream.Collectors.toList());
            java.util.List<String> payloads = redisTemplate.opsForValue().multiGet(keys);
            if (payloads == null) {
                continue;
            }
            for (int index = 0; index < payloads.size(); index++) {
                NewsItemClassification value = deserialize(payloads.get(index));
                if (value != null) {
                    result.put(batch.get(index), value);
                }
            }
        }
        return result;
    }

    private NewsItemClassification pending(String itemId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        NewsItemClassification value = new NewsItemClassification();
        value.setItemId(itemId);
        value.setStatus(NewsClassificationStatus.PENDING.name());
        value.setCreatedAt(createdAt);
        value.setUpdatedAt(updatedAt);
        return value;
    }

    private NewsItemClassification read(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(key(itemId));
            return deserialize(payload);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Redis 新闻分类缓存读取失败", error);
        }
    }

    private NewsItemClassification deserialize(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readerFor(NewsItemClassification.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Redis 新闻分类缓存读取失败", error);
        }
    }

    private void write(NewsItemClassification value, LocalDateTime now) {
        Duration ttl = Duration.between(now, value.getCreatedAt().plusHours(normalizedTtlHours()));
        String cacheKey = key(value.getItemId());
        if (ttl.isNegative() || ttl.isZero()) {
            redisTemplate.delete(cacheKey);
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value),
                    ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException | JsonProcessingException error) {
            throw new IllegalStateException("Redis 新闻分类缓存写入失败", error);
        }
    }

    private String key(String itemId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(itemId.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(KEY_PREFIX);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", error);
        }
    }

    private int normalizedTtlHours() {
        return Math.max(1, properties.getTtlHours());
    }
}
