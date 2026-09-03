package com.finscope.dao.industrychain;

import com.finscope.dao.radar.RedisRadarCacheStore;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 产业链与临时雷达事件的影响关系，生命周期跟随 36 小时雷达缓存。 */
@Repository
public class IndustryChainEventImpactRepository {
    @Resource
    private RedisRadarCacheStore store;

    public boolean upsert(IndustryChainEventImpact impact, LocalDateTime now) {
        return store.update(state -> {
            Map<Long, IndustryChainEventImpact> byEvent = state.getIndustryChainImpacts()
                    .computeIfAbsent(impact.getChainId(), ignored -> new LinkedHashMap<Long, IndustryChainEventImpact>());
            IndustryChainEventImpact existing = byEvent.get(impact.getRadarEventId());
            boolean created = existing == null;
            impact.setId(existing == null ? state.nextSequence() : existing.getId());
            impact.setCreatedAt(existing == null || existing.getCreatedAt() == null ? now : existing.getCreatedAt());
            impact.setUpdatedAt(now);
            byEvent.put(impact.getRadarEventId(), impact);
            return created;
        });
    }

    public List<IndustryChainEventImpact> findByChainId(Long chainId) {
        List<IndustryChainEventImpact> result = new ArrayList<IndustryChainEventImpact>(store.read()
                .getIndustryChainImpacts().getOrDefault(chainId, new LinkedHashMap<Long, IndustryChainEventImpact>())
                .values());
        result.sort(Comparator.comparing(IndustryChainEventImpact::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(IndustryChainEventImpact::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public int countByChainId(Long chainId) {
        return store.read().getIndustryChainImpacts()
                .getOrDefault(chainId, new LinkedHashMap<Long, IndustryChainEventImpact>()).size();
    }

    public Set<Long> findRadarEventIds(Long chainId) {
        return new LinkedHashSet<Long>(store.read().getIndustryChainImpacts()
                .getOrDefault(chainId, new LinkedHashMap<Long, IndustryChainEventImpact>()).keySet());
    }

    public Map<Long, String> findAnalysisVersionsByRadarEventId(Long chainId) {
        Map<Long, String> result = new LinkedHashMap<Long, String>();
        for (IndustryChainEventImpact impact : findByChainId(chainId)) {
            result.put(impact.getRadarEventId(), impact.getAnalysisVersion());
        }
        return result;
    }
}
