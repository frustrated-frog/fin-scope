package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RadarEventBatchIdentityResolver {
    @Resource
    private RadarRepository repository;

    public RadarEventIdentityResolution resolve(List<RadarClusteringService.ClusterResult> clusters) {
        List<IdentityCandidate> candidates = reserveNativeIdentities(clusters);
        candidates.sort(candidatePriority());
        Set<String> assignedFinalKeys = new HashSet<String>();
        Set<String> reservedNativeKeys = nativeKeys(candidates);
        Map<String, Optional<RadarEvent>> existingEvents = new HashMap<String, Optional<RadarEvent>>();
        int legacyReusedCount = 0;
        int legacyConflictCount = 0;
        int fallbackKeptCount = 0;
        for (IdentityCandidate candidate : candidates) {
            Optional<RadarEvent> exact = findExisting(candidate.nativeKey, existingEvents);
            if (exact.isPresent()) {
                assign(candidate, candidate.nativeKey, assignedFinalKeys);
                continue;
            }
            ReuseDecision decision = reusableLegacyKey(candidate, reservedNativeKeys,
                    assignedFinalKeys, existingEvents);
            legacyConflictCount += decision.conflictCount;
            if (decision.eventKey != null) {
                assign(candidate, decision.eventKey, assignedFinalKeys);
                legacyReusedCount++;
                continue;
            }
            assign(candidate, candidate.nativeKey, assignedFinalKeys);
            if (decision.conflictCount > 0) {
                fallbackKeptCount++;
            }
        }
        return RadarEventIdentityResolution.builder()
                .nativeIdentityCount(candidates.size())
                .legacyReusedCount(legacyReusedCount)
                .legacyConflictCount(legacyConflictCount)
                .fallbackKeptCount(fallbackKeptCount)
                .build();
    }

    private List<IdentityCandidate> reserveNativeIdentities(
            List<RadarClusteringService.ClusterResult> clusters) {
        List<IdentityCandidate> candidates = new ArrayList<IdentityCandidate>();
        Set<String> nativeKeys = new HashSet<String>();
        if (clusters == null) {
            return candidates;
        }
        for (RadarClusteringService.ClusterResult cluster : clusters) {
            RadarEvent event = cluster == null ? null : cluster.getEvent();
            String eventKey = event == null ? null : event.getEventKey();
            if (eventKey == null || eventKey.trim().isEmpty()) {
                throw new IllegalStateException("生产批次出现空事件身份");
            }
            String nativeKey = eventKey.trim();
            if (!nativeKeys.add(nativeKey)) {
                throw new IllegalStateException("聚类合并后仍出现重复原生事件身份: " + nativeKey);
            }
            candidates.add(new IdentityCandidate(cluster, nativeKey));
        }
        return candidates;
    }

    private Set<String> nativeKeys(List<IdentityCandidate> candidates) {
        Set<String> values = new HashSet<String>();
        for (IdentityCandidate candidate : candidates) {
            values.add(candidate.nativeKey);
        }
        return values;
    }

    private Comparator<IdentityCandidate> candidatePriority() {
        return Comparator.comparingInt(IdentityCandidate::signalCount).reversed()
                .thenComparing(Comparator.comparingInt(IdentityCandidate::sourceCount).reversed())
                .thenComparing(IdentityCandidate::lastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(IdentityCandidate::nativeKey);
    }

    private ReuseDecision reusableLegacyKey(IdentityCandidate candidate,
                                            Set<String> reservedNativeKeys,
                                            Set<String> assignedFinalKeys,
                                            Map<String, Optional<RadarEvent>> existingEvents) {
        int conflicts = 0;
        for (String legacyKey : legacyKeys(candidate, existingEvents)) {
            if (reservedNativeKeys.contains(legacyKey) || assignedFinalKeys.contains(legacyKey)) {
                conflicts++;
                continue;
            }
            return new ReuseDecision(legacyKey, conflicts);
        }
        return new ReuseDecision(null, conflicts);
    }

    private List<String> legacyKeys(IdentityCandidate candidate,
                                    Map<String, Optional<RadarEvent>> existingEvents) {
        List<String> values = new ArrayList<String>();
        String[] parts = candidate.nativeKey.split(":");
        if (parts.length < 4) {
            return values;
        }
        if (parts.length > 4) {
            String dateOnlyKey = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3];
            if (findExisting(dateOnlyKey, existingEvents).isPresent()) {
                values.add(dateOnlyKey);
            }
        }
        String legacyKey = parts[0] + ":" + parts[1] + ":" + parts[2];
        Optional<RadarEvent> legacy = findExisting(legacyKey, existingEvents);
        if (legacy.isPresent() && sameDate(legacy.get().getLastSeenAt(), candidate.lastSeenAt())) {
            values.add(legacyKey);
        }
        String categoryLegacyKey = legacyCategoryKey(candidate.event(), legacyKey);
        Optional<RadarEvent> categoryLegacy = findExisting(categoryLegacyKey, existingEvents);
        if (categoryLegacy.isPresent()
                && sameDate(categoryLegacy.get().getLastSeenAt(), candidate.lastSeenAt())) {
            values.add(categoryLegacyKey);
        }
        return values;
    }

    private Optional<RadarEvent> findExisting(String eventKey,
                                              Map<String, Optional<RadarEvent>> existingEvents) {
        Optional<RadarEvent> existing = existingEvents.get(eventKey);
        if (existing != null) {
            return existing;
        }
        Optional<RadarEvent> loaded = repository.findEventByKey(eventKey);
        existingEvents.put(eventKey, loaded);
        return loaded;
    }

    private void assign(IdentityCandidate candidate, String eventKey, Set<String> assignedFinalKeys) {
        if (!assignedFinalKeys.add(eventKey)) {
            throw new IllegalStateException("身份解析后仍出现重复事件身份: " + eventKey);
        }
        candidate.event().setEventKey(eventKey);
    }

    private String legacyCategoryKey(RadarEvent event, String legacyKey) {
        String category = event.getCategoryCode() == null
                ? "UNCLASSIFIED" : event.getCategoryCode().trim().toUpperCase(Locale.ROOT);
        return category + ":" + legacyKey;
    }

    private boolean sameDate(LocalDateTime left, LocalDateTime right) {
        return left != null && right != null && left.toLocalDate().equals(right.toLocalDate());
    }

    private static final class IdentityCandidate {
        private final RadarClusteringService.ClusterResult cluster;
        private final String nativeKey;

        private IdentityCandidate(RadarClusteringService.ClusterResult cluster, String nativeKey) {
            this.cluster = cluster;
            this.nativeKey = nativeKey;
        }

        private RadarEvent event() {
            return cluster.getEvent();
        }

        private String nativeKey() {
            return nativeKey;
        }

        private int signalCount() {
            return cluster.getSignals().size();
        }

        private int sourceCount() {
            return event().getSourceCount();
        }

        private LocalDateTime lastSeenAt() {
            return event().getLastSeenAt();
        }
    }

    private static final class ReuseDecision {
        private final String eventKey;
        private final int conflictCount;

        private ReuseDecision(String eventKey, int conflictCount) {
            this.eventKey = eventKey;
            this.conflictCount = conflictCount;
        }
    }
}
