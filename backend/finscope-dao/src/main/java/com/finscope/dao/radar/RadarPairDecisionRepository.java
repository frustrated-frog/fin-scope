package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarPairDecision;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class RadarPairDecisionRepository {
    @Resource
    private RedisRadarCacheStore store;

    public void save(RadarPairDecision value) {
        store.update(state -> {
            RadarPairDecision existing = state.getPairDecisions().get(value.getPairKey());
            LocalDateTime updatedAt = value.getUpdatedAt() == null ? LocalDateTime.now() : value.getUpdatedAt();
            value.setCreatedAt(existing != null && existing.getCreatedAt() != null
                    ? existing.getCreatedAt() : value.getCreatedAt() == null ? updatedAt : value.getCreatedAt());
            value.setUpdatedAt(updatedAt);
            state.getPairDecisions().put(value.getPairKey(), value);
            return null;
        });
    }

    public Optional<RadarPairDecision> find(String pairKey) {
        return Optional.ofNullable(store.read().getPairDecisions().get(pairKey));
    }
}
