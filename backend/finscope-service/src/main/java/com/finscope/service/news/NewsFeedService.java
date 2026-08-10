package com.finscope.service.news;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.finscope.common.exception.BizErrorCode;

@Service
public class NewsFeedService {
    private final ResearchMaterialGateway gateway;
    private final NewsClassificationRepository classifications;
    private final NewsCategoryRepository categories;
    private final NewsClassificationCoordinator coordinator;
    private final Clock clock;

    @Autowired
    public NewsFeedService(ResearchMaterialGateway gateway,
                           NewsClassificationRepository classifications,
                           NewsCategoryRepository categories,
                           NewsClassificationCoordinator coordinator) {
        this(gateway, classifications, categories, coordinator, Clock.systemDefaultZone());
    }

    NewsFeedService(ResearchMaterialGateway gateway, Clock clock) {
        this(gateway, null, null, null, clock);
    }

    NewsFeedService(ResearchMaterialGateway gateway,
                    NewsClassificationRepository classifications,
                    NewsCategoryRepository categories,
                    NewsClassificationCoordinator coordinator,
                    Clock clock) {
        this.gateway = gateway;
        this.classifications = classifications;
        this.categories = categories;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public NewsFeedSnapshot load(int requestedLimit) {
        return load("ALL", requestedLimit);
    }

    public NewsFeedSnapshot load(String requestedCategory, int requestedLimit) {
        String category = normalizeCategory(requestedCategory);
        List<NewsCategory> enabledCategories = new ArrayList<NewsCategory>(categories());
        if (!"ALL".equals(category) && !"PENDING_REVIEW".equals(category)) {
            NewsCategory selected = categories == null ? null : categories.findEnabledByCode(category).orElse(null);
            if (selected == null) {
                throw new BusinessException(BizErrorCode.NEWS_CATEGORY_UNKNOWN, category);
            }
            if (!containsCategory(enabledCategories, selected.getCode())) enabledCategories.add(selected);
        }

        int limit = Math.max(1, Math.min(requestedLimit, 100));
        ResearchMaterialGatewayResult result = gateway.readNewsFlashSources(
                new ResearchMaterialRequest("000001", "", 50));
        List<ResearchMaterial> ordered = new ArrayList<ResearchMaterial>(result.getMaterials());
        ordered.sort(Comparator.comparing(ResearchMaterial::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, ResearchMaterial> unique = new LinkedHashMap<String, ResearchMaterial>();
        for (ResearchMaterial material : ordered) {
            String key = blank(material.getUrl())
                    ? material.getProviderCode() + "|" + material.getExternalId()
                    : material.getUrl().trim();
            if (!unique.containsKey(key)) unique.put(key, material);
        }

        List<NewsFeedItem> baseItems = new ArrayList<NewsFeedItem>();
        for (ResearchMaterial material : unique.values()) baseItems.add(map(material));
        Map<String, NewsItemClassification> saved = classifications == null
                ? Collections.emptyMap()
                : classifications.findByItemIds(itemIds(baseItems));
        if (coordinator != null) coordinator.schedule(candidates(baseItems));

        Map<String, String> categoryNames = new LinkedHashMap<String, String>();
        for (NewsCategory value : enabledCategories) categoryNames.put(value.getCode(), value.getName());
        Map<String, Integer> categoryCounts = categoryCounts(baseItems, saved, enabledCategories);
        int unclassifiedCount = unclassifiedCount(baseItems, saved);
        List<NewsFeedItem> items = new ArrayList<NewsFeedItem>();
        Set<String> sources = new LinkedHashSet<String>();
        for (NewsFeedItem item : baseItems) {
            NewsItemClassification classification = saved.get(item.getId());
            if (!matches(category, classification)) continue;
            if (items.size() >= limit) break;
            NewsFeedItem enriched = enrich(item, classification, categoryNames);
            items.add(enriched);
            sources.add(enriched.getSourceName());
        }
        return new NewsFeedSnapshot(items, result.getWarnings(), LocalDateTime.now(clock), sources.size(),
                categoryCounts, unclassifiedCount);
    }

    public List<NewsCategory> categories() {
        return categories == null ? Collections.emptyList() : categories.findEnabled();
    }

    private boolean matches(String requestedCategory, NewsItemClassification classification) {
        if ("ALL".equals(requestedCategory)) return true;
        if (classification == null || !"CLASSIFIED".equals(classification.getStatus())) return false;
        if ("PENDING_REVIEW".equals(requestedCategory)) return classification.isPendingReview();
        return requestedCategory.equals(classification.getEffectiveCategoryCode());
    }

    private NewsFeedItem map(ResearchMaterial value) {
        String provider = value.getProviderCode();
        String kind = provider != null && provider.endsWith("_DIGEST") ? "ARTICLE" : "FLASH";
        String id = (provider == null ? "NEWS" : provider) + ":" + value.getExternalId();
        return new NewsFeedItem(id, kind, value.getTitle(), value.getContent(), value.getUrl(),
                value.getPublishedAt(), provider, sourceName(value.getProviderFamily()), value.getSourceTier());
    }

    private NewsFeedItem enrich(NewsFeedItem item, NewsItemClassification classification,
                                Map<String, String> categoryNames) {
        return new NewsFeedItem(item.getId(), item.getKind(), item.getTitle(), item.getContent(), item.getUrl(),
                item.getPublishedAt(), item.getProviderCode(), item.getSourceName(), item.getSourceTier(),
                classification == null ? null : classification.getEffectiveCategoryCode(),
                classification == null ? null : categoryNames.get(classification.getEffectiveCategoryCode()),
                classification == null ? null : classification.getCategoryCode(),
                classification == null ? null : classification.getConfidence(),
                classification == null ? null : classification.getReason(),
                classification == null ? null : classification.getReviewStatus(),
                classification != null && classification.isManuallyReviewed(),
                classification == null ? null : classification.getManualReason());
    }

    private Map<String, Integer> categoryCounts(List<NewsFeedItem> items,
                                                Map<String, NewsItemClassification> saved,
                                                List<NewsCategory> enabledCategories) {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        values.put("ALL", items.size());
        for (NewsCategory category : enabledCategories) values.put(category.getCode(), 0);
        values.put("PENDING_REVIEW", 0);
        for (NewsFeedItem item : items) {
            NewsItemClassification classification = saved.get(item.getId());
            if (classification == null || !"CLASSIFIED".equals(classification.getStatus())) continue;
            String effective = classification.getEffectiveCategoryCode();
            if (values.containsKey(effective)) values.put(effective, values.get(effective) + 1);
            if (classification.isPendingReview()) {
                values.put("PENDING_REVIEW", values.get("PENDING_REVIEW") + 1);
            }
        }
        return values;
    }

    private int unclassifiedCount(List<NewsFeedItem> items, Map<String, NewsItemClassification> saved) {
        int count = 0;
        for (NewsFeedItem item : items) {
            NewsItemClassification classification = saved.get(item.getId());
            if (classification == null || !"CLASSIFIED".equals(classification.getStatus())) count++;
        }
        return count;
    }

    private boolean containsCategory(List<NewsCategory> values, String code) {
        for (NewsCategory value : values) if (code.equals(value.getCode())) return true;
        return false;
    }

    private List<String> itemIds(List<NewsFeedItem> items) {
        List<String> values = new ArrayList<String>();
        for (NewsFeedItem item : items) values.add(item.getId());
        return values;
    }

    private List<NewsClassificationCandidate> candidates(List<NewsFeedItem> items) {
        List<NewsClassificationCandidate> values = new ArrayList<NewsClassificationCandidate>();
        for (NewsFeedItem item : items) {
            values.add(new NewsClassificationCandidate(item.getId(), item.getTitle(), item.getContent(),
                    item.getSourceName(), item.getPublishedAt()));
        }
        return values;
    }

    private String normalizeCategory(String value) {
        return blank(value) ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String sourceName(String family) {
        if ("CLS".equals(family)) return "财联社";
        if ("THS".equals(family)) return "同花顺";
        if ("EASTMONEY".equals(family)) {
            return "东方财富";
        }
        return blank(family) ? "公开资讯" : family;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
