package com.finscope.service.globalexpectations;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 保留最近 24 小时的公共概率快照，不承担长期持久化职责。 */
@Component
public class GlobalExpectationSnapshotCache {
    private static final Duration RETENTION = Duration.ofHours(24);
    private final Map<String, List<Snapshot>> histories = new HashMap<String, List<Snapshot>>();

    public synchronized void record(String marketKey, Instant observedAt, Integer probability) {
        if (marketKey == null || marketKey.isBlank() || observedAt == null || probability == null) {
            return;
        }
        List<Snapshot> snapshots = histories.computeIfAbsent(marketKey, ignored -> new ArrayList<Snapshot>());
        if (!snapshots.isEmpty() && snapshots.get(snapshots.size() - 1).probability == probability) {
            prune(snapshots, observedAt);
            return;
        }
        snapshots.add(new Snapshot(observedAt, probability));
        prune(snapshots, observedAt);
    }

    public synchronized Double changeSince(String marketKey, Instant observedAt, Integer probability, Duration window) {
        if (observedAt == null || probability == null || window == null) {
            return null;
        }
        List<Snapshot> snapshots = histories.get(marketKey);
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        Instant target = observedAt.minus(window);
        Snapshot baseline = null;
        for (Snapshot snapshot : snapshots) {
            if (!snapshot.observedAt.isAfter(target)) {
                baseline = snapshot;
            }
        }
        return baseline == null ? null : probability.doubleValue() - baseline.probability;
    }

    public synchronized List<Snapshot> history(String marketKey) {
        List<Snapshot> snapshots = histories.get(marketKey);
        if (snapshots == null) {
            return List.of();
        }
        return new ArrayList<Snapshot>(snapshots);
    }

    private void prune(List<Snapshot> snapshots, Instant observedAt) {
        Instant cutoff = observedAt.minus(RETENTION);
        snapshots.removeIf(snapshot -> snapshot.observedAt.isBefore(cutoff));
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.observedAt));
    }

    public static final class Snapshot {
        private final Instant observedAt;
        private final int probability;

        private Snapshot(Instant observedAt, int probability) {
            this.observedAt = observedAt;
            this.probability = probability;
        }

        public Instant getObservedAt() {
            return observedAt;
        }

        public int getProbability() {
            return probability;
        }
    }
}
