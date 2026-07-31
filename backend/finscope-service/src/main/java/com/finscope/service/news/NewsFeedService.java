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
        List<NewsCategory> enabledCategories;
        if ("ALL".equals(category)) {
            enabledCategories = categories();
        } else {
            NewsCategory selected = categories == null ? null : categories.findEnabledByCode(category).orElse(null);
            if (selected == null) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        "未知或已停用的资讯分类：" + category);
            }
            enabledCategories = Collections.singletonList(selected);
        }

        int limit = Math.max(1, Math.min(requestedLimit, 100));
        ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
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
        return new NewsFeedSnapshot(items, result.getWarnings(), LocalDateTime.now(clock), sources.size());
    }

    public List<NewsCategory> categories() {
        return categories == null ? Collections.emptyList() : categories.findEnabled();
    }

    private boolean matches(String requestedCategory, NewsItemClassification classification) {
        return "ALL".equals(requestedCategory) || (classification != null
                && "CLASSIFIED".equals(classification.getStatus())
                && requestedCategory.equals(classification.getCategoryCode()));
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
                classification == null ? null : classification.getCategoryCode(),
                classification == null ? null : categoryNames.get(classification.getCategoryCode()),
                classification == null ? null : classification.getConfidence(),
                classification == null ? null : classification.getReason());
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
        return blank(family) ? "公开资讯" : family;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
