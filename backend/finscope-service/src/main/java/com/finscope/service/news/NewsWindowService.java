package com.finscope.service.news;

import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.dao.news.NewsWindowRepository;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NewsWindowService {
    @Autowired
    private NewsWindowRepository repository;
    @Autowired
    private NewsClassificationRepository classifications;
    @Autowired
    private NewsClassificationCoordinator coordinator;
    @Autowired
    private NewsFeedService feed;
    @Autowired
    private ResearchMaterialGateway gateway;
    @Autowired
    private EphemeralContentCacheProperties properties;
    private Clock clock = Clock.systemDefaultZone();

    public void capture(List<ResearchMaterial> materials) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusHours(retentionHours());
        repository.merge(materials, now, cutoff);
        List<NewsClassificationCandidate> candidates = new ArrayList<>();
        for (ResearchMaterial material : materials) {
            if (material.getProviderCode() != null && material.getExternalId() != null
                    && material.getPublishedAt() != null && !material.getPublishedAt().isBefore(cutoff)
                    && !material.getPublishedAt().isAfter(now)) {
                NewsFeedItem item = feed.map(material);
                candidates.add(new NewsClassificationCandidate(item.getId(), item.getTitle(), item.getContent(),
                        item.getSourceName(), item.getPublishedAt()));
            }
        }
        coordinator.schedule(candidates);
    }

    public NewsFeedSnapshot load(String category, String provider, String keyword, int hours,
                                 int requestedPage, int requestedSize, LocalDateTime requestedAnchor) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime anchor = requestedAnchor == null || requestedAnchor.isAfter(now) ? now : requestedAnchor;
        int windowHours = Math.max(1, Math.min(hours, retentionHours()));
        LocalDateTime from = anchor.minusHours(windowHours);
        if (from.isBefore(now.minusHours(retentionHours()))) {
            from = now.minusHours(retentionHours());
        }
        String selected = category == null || category.isBlank() ? "ALL" : category.trim().toUpperCase(Locale.ROOT);
        List<NewsCategory> categories = feed.categories();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ALL", 0);
        counts.put("PENDING_REVIEW", 0);
        for (NewsCategory value : categories) {
            names.put(value.getCode(), value.getName());
            counts.put(value.getCode(), 0);
        }
        if (!counts.containsKey(selected)) {
            throw new BusinessException(BizErrorCode.NEWS_CATEGORY_UNKNOWN, selected);
        }
        List<String> candidates = repository.findIds(from, anchor, provider, keyword);
        Map<String, NewsItemClassification> saved = classifications.findByItemIds(candidates);
        List<String> matching = new ArrayList<>();
        counts.put("ALL", candidates.size());
        int unclassified = 0;
        for (String id : candidates) {
            NewsItemClassification value = saved.get(id);
            if (value == null || !"CLASSIFIED".equals(value.getStatus())) {
                unclassified++;
            } else {
                counts.computeIfPresent(value.getEffectiveCategoryCode(), (key, count) -> count + 1);
                if (value.isPendingReview()) {
                    counts.compute("PENDING_REVIEW", (key, count) -> count + 1);
                }
            }
            if (feed.matches(selected, value)) {
                matching.add(id);
            }
        }
        int size = Math.max(1, Math.min(requestedSize, 100));
        int pages = (matching.size() + size - 1) / size;
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, pages - 1)));
        int start = page * size;
        List<NewsFeedItem> items = new ArrayList<>();
        for (ResearchMaterial material : repository.findByIds(matching.subList(start, Math.min(start + size, matching.size())))) {
            NewsFeedItem item = feed.map(material);
            items.add(feed.enrich(item, saved.get(item.getId()), names));
        }
        var sourceStatus = gateway.readNewsFlashSources(new ResearchMaterialRequest("000001", "", 50));
        List<String> providers = repository.providers(from, anchor);
        NewsFeedSnapshot result = new NewsFeedSnapshot(items, sourceStatus.getWarnings(), now,
                providers.size(), counts, unclassified);
        result.setSourceHealth(sourceStatus.getSourceHealth());
        result.setSourceOptions(providers);
        result.setPage(page);
        result.setPageSize(size);
        result.setTotalCount(matching.size());
        result.setTotalPages(pages);
        result.setWindowHours(windowHours);
        result.setAsOf(anchor);
        return result;
    }

    public java.util.Optional<NewsFeedItem> find(String itemId) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(retentionHours());
        List<ResearchMaterial> materials = repository.findByIds(java.util.Collections.singletonList(itemId));
        Map<String, NewsItemClassification> saved = classifications.findByItemIds(java.util.Collections.singletonList(itemId));
        Map<String, String> names = new LinkedHashMap<>();
        for (NewsCategory category : feed.categories()) {
            names.put(category.getCode(), category.getName());
        }
        return materials.stream().filter(material -> material.getPublishedAt() != null
                && !material.getPublishedAt().isBefore(cutoff))
                .findFirst().map(material -> feed.enrich(feed.map(material), saved.get(itemId), names));
    }

    private int retentionHours() {
        return Math.max(1, properties.getTtlHours());
    }
}
