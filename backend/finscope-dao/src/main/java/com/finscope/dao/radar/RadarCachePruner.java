package com.finscope.dao.radar;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 迁移时修复遗留悬空数据；运行期间由独立对象 TTL 与父记录校验防止再次产生。 */
final class RadarCachePruner {
    private RadarCachePruner() {
    }

    static void prune(RadarCacheState state, LocalDateTime now, int ttlHours, ZoneId zone) {
        long epoch = now.atZone(zone).toInstant().toEpochMilli();
        LocalDateTime cutoff = now.minusHours(ttlHours);
        state.getSignals().entrySet().removeIf(entry -> entry.getValue() == null
                || RadarCacheExpiry.deadline("signals", entry.getKey(), entry.getValue(), state, now, ttlHours, zone) <= epoch);
        state.getEvents().entrySet().removeIf(entry -> entry.getValue() == null
                || RadarCacheExpiry.deadline("events", entry.getKey(), entry.getValue(), state, now, ttlHours, zone) <= epoch);
        state.getSignalIdsByItemId().values().removeIf(id -> !state.getSignals().containsKey(id));
        state.getEventIdsByKey().values().removeIf(id -> !state.getEvents().containsKey(id));
        List<Map<Long, ?>> owned = List.of(state.getEventSignals(), state.getSnapshots(), state.getEvidence(),
                state.getInterpretations(), state.getUserStates(), state.getTimelines(),
                state.getTimelineFingerprints(), state.getResearchLinks());
        for (Map<Long, ?> children : owned) {
            children.keySet().removeIf(id -> !state.getEvents().containsKey(id));
        }
        state.getEventSignals().values().forEach(links -> links.removeIf(link ->
                link == null || !state.getSignals().containsKey(link.getSignalId())));
        state.getEventSignals().values().removeIf(List::isEmpty);
        state.getSnapshots().values().forEach(values -> values.removeIf(value ->
                value.getSnapshotAt() == null || value.getSnapshotAt().isBefore(now.minusDays(RadarCacheLayout.SNAPSHOT_KEEP_DAYS))));
        state.getSnapshots().values().removeIf(List::isEmpty);
        state.getNotifications().removeIf(value -> !state.getEvents().containsKey(value.getEventId())
                || value.getCreatedAt() == null || value.getCreatedAt().isBefore(cutoff));
        state.getAgentRunsBySubject().entrySet().removeIf(entry -> entry.getKey().startsWith("RADAR_EVENT:")
                && !state.getEvents().containsKey(Long.valueOf(entry.getKey().substring("RADAR_EVENT:".length()))));
        state.getAgentRunsBySubject().values().forEach(values -> values.removeIf(value ->
                value.getCreatedAt() == null || value.getCreatedAt().isBefore(cutoff)));
        state.getAgentRunsBySubject().values().removeIf(List::isEmpty);
        state.getIndustryChainImpacts().values().forEach(values ->
                values.keySet().removeIf(id -> !state.getEvents().containsKey(id)));
        state.getIndustryChainImpacts().values().removeIf(Map::isEmpty);
        state.getPairDecisions().entrySet().removeIf(entry -> entry.getValue().getUpdatedAt() == null
                || entry.getValue().getUpdatedAt().isBefore(cutoff));
        state.getRuns().entrySet().removeIf(entry -> entry.getValue().getStartedAt() == null
                || entry.getValue().getStartedAt().isBefore(cutoff));
        state.getRunSteps().keySet().removeIf(id -> !state.getRuns().containsKey(id));
    }
}
