package com.finscope.service.news;

import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NewsFeedService {
    private final ResearchMaterialGateway gateway;
    private final Clock clock;

    public NewsFeedService(ResearchMaterialGateway gateway) {
        this(gateway, Clock.systemDefaultZone());
    }

    NewsFeedService(ResearchMaterialGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public NewsFeedSnapshot load(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        ResearchMaterialGatewayResult result = gateway.search(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "", 50));
        List<ResearchMaterial> ordered = new ArrayList<ResearchMaterial>(result.getMaterials());
        ordered.sort(Comparator.comparing(ResearchMaterial::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, NewsFeedItem> unique = new LinkedHashMap<String, NewsFeedItem>();
        Set<String> sources = new LinkedHashSet<String>();
        for (ResearchMaterial material : ordered) {
            if (unique.size() >= limit) break;
            String key = blank(material.getUrl())
                    ? material.getProviderCode() + "|" + material.getExternalId()
                    : material.getUrl().trim();
            if (unique.containsKey(key)) continue;
            sources.add(material.getProviderFamily());
            unique.put(key, map(material));
        }
        return new NewsFeedSnapshot(new ArrayList<NewsFeedItem>(unique.values()), result.getWarnings(),
                LocalDateTime.now(clock), sources.size());
    }

    private NewsFeedItem map(ResearchMaterial value) {
        String provider = value.getProviderCode();
        String kind = provider != null && provider.endsWith("_DIGEST") ? "ARTICLE" : "FLASH";
        String id = (provider == null ? "NEWS" : provider) + ":" + value.getExternalId();
        return new NewsFeedItem(id, kind, value.getTitle(), value.getContent(), value.getUrl(),
                value.getPublishedAt(), provider, sourceName(value.getProviderFamily()), value.getSourceTier());
    }

    private String sourceName(String family) {
        if ("CLS".equals(family)) return "财联社";
        if ("THS".equals(family)) return "同花顺";
        return blank(family) ? "公开资讯" : family;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
