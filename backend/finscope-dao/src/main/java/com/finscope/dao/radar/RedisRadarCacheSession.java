package com.finscope.dao.radar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarEventWorkspace;

import java.time.LocalDateTime;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 显式传递的一次读取/更新上下文，既不共享可变对象，也不使用 ThreadLocal。 */
final class RedisRadarCacheSession extends RadarCacheState {
    private final RedisRadarCacheStore store;
    private final String generation;
    private final String revision;
    private final LocalDateTime now;
    private final Map<String, RedisRadarRecordMap<?, ?>> records = new LinkedHashMap<>();
    private final Map<String, RadarCacheLayout.Section> sections = new LinkedHashMap<>();
    private final RedisRadarRecordMap<Long, RadarEventWorkspace.Notification> notifications;
    private final RedisRadarRecordMap<String, String> notificationFingerprints;

    RedisRadarCacheSession(RedisRadarCacheStore store, String generation, String revision, LocalDateTime now) {
        this.store = store;
        this.generation = generation;
        this.revision = revision;
        this.now = now;
        for (RadarCacheLayout.Section section : RadarCacheLayout.sections(store.mapper())) {
            sections.put(section.name(), section);
            records.put(section.name(), new RedisRadarRecordMap<>(this, section));
        }
        setSignals(map("signals"));
        setSignalIdsByItemId(map("signalIdsByItemId"));
        setEvents(map("events"));
        setEventIdsByKey(map("eventIdsByKey"));
        setEventSignals(map("eventSignals"));
        setSnapshots(map("snapshots"));
        setEvidence(map("evidence"));
        setInterpretations(map("interpretations"));
        setPairDecisions(map("pairDecisions"));
        setRuns(map("runs"));
        setRunSteps(map("runSteps"));
        setUserStates(map("userStates"));
        setTimelines(map("timelines"));
        setTimelineFingerprints(map("timelineFingerprints"));
        setResearchLinks(map("researchLinks"));
        setAgentRunsBySubject(map("agentRunsBySubject"));
        setIndustryChainImpacts(map("industryChainImpacts"));
        notifications = new RedisRadarRecordMap<>(this, RadarCacheLayout.notifications(store.mapper()));
        notificationFingerprints = new RedisRadarRecordMap<>(this, RadarCacheLayout.notificationFingerprints(store.mapper()));
        records.put("notifications", notifications);
        records.put("notificationFingerprints", notificationFingerprints);
        setNotifications(new NotificationList());
        setNotificationFingerprints(new AbstractSet<>() {
            @Override
            public boolean add(String value) {
                return notificationFingerprints.put(value, "1") == null;
            }

            @Override
            public Iterator<String> iterator() {
                return notificationFingerprints.keySet().iterator();
            }

            @Override
            public int size() {
                return notificationFingerprints.size();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <K, V> RedisRadarRecordMap<K, V> map(String name) {
        return (RedisRadarRecordMap<K, V>) records.get(name);
    }

    @Override
    public long nextSequence() {
        return store.nextSequence(generation);
    }

    String generation() {
        return generation;
    }

    String revision() {
        return revision;
    }

    List<String> readRecords(RadarCacheLayout.Section section, Collection<?> keys) {
        return store.readRecords(generation, section, keys);
    }

    Set<String> members(RadarCacheLayout.Section section) {
        return store.members(generation, section, now);
    }

    void prefetchDependencies(RadarCacheLayout.Section section, Collection<?> keys, List<String> payloads) {
        List<Object> presentKeys = new ArrayList<>();
        int position = 0;
        for (Object key : keys) {
            if (payloads.get(position++) != null) {
                presentKeys.add(key);
            }
        }
        switch (section.name()) {
            case "signalIdsByItemId" -> prefetch(getSignals(), referencedIds(payloads));
            case "eventIdsByKey" -> prefetch(getEvents(), referencedIds(payloads));
            case "eventSignals", "snapshots", "evidence", "interpretations", "userStates",
                    "timelines", "timelineFingerprints", "researchLinks" ->
                    prefetch(getEvents(), presentKeys.stream().map(Long.class::cast).toList());
            case "runSteps" -> prefetch(getRuns(), presentKeys.stream().map(Long.class::cast).toList());
            default -> {
                // 独立对象无需预取父记录。
            }
        }
    }

    private List<Long> referencedIds(List<String> payloads) {
        return payloads.stream().filter(java.util.Objects::nonNull).map(String::trim).map(Long::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    <V> V decode(RadarCacheLayout.Section section, Object key, String payload) {
        if (payload == null) {
            return null;
        }
        try {
            Object value = store.mapper().readerFor(section.valueType())
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(payload);
            if (deadline(section, key, value) <= store.epochMillis(now)) {
                return null;
            }
            if (value instanceof Collection<?> values) {
                pruneCollection(section.name(), values);
            }
            if ("industryChainImpacts".equals(section.name())) {
                Map<Long, IndustryChainEventImpact> impacts = (Map<Long, IndustryChainEventImpact>) value;
                prefetch(getEvents(), impacts.keySet());
                impacts.keySet().removeIf(id -> !getEvents().containsKey(id));
            }
            return (V) value;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Redis 雷达记录解析失败: " + section.name(), error);
        }
    }

    @SuppressWarnings("unchecked")
    private void pruneCollection(String name, Collection<?> values) {
        LocalDateTime cutoff = now.minusHours(store.ttlHours());
        if ("eventSignals".equals(name)) {
            List<RadarEventSignal> links = (List<RadarEventSignal>) values;
            prefetch(getSignals(), links.stream().map(RadarEventSignal::getSignalId).toList());
            links.removeIf(link -> !getSignals().containsKey(link.getSignalId()));
        } else if ("agentRunsBySubject".equals(name)) {
            ((List<AgentRun>) values).removeIf(value -> value.getCreatedAt() == null || value.getCreatedAt().isBefore(cutoff));
        } else if ("timelines".equals(name)) {
            ((List<RadarEventWorkspace.TimelineEntry>) values).removeIf(value ->
                    value.getOccurredAt() == null || value.getOccurredAt().isBefore(cutoff));
        } else if ("snapshots".equals(name)) {
            ((List<RadarEventSnapshot>) values).removeIf(value -> value.getSnapshotAt() == null
                    || value.getSnapshotAt().isBefore(now.minusDays(RadarCacheLayout.SNAPSHOT_KEEP_DAYS)));
        }
    }

    String encode(Object value) {
        try {
            return store.mapper().writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Redis 雷达记录序列化失败", error);
        }
    }

    long deadline(RadarCacheLayout.Section section, Object key, Object value) {
        return RadarCacheExpiry.deadline(section.name(), key, value, this, now,
                store.ttlHours(), store.zone());
    }

    String parentKey(RadarCacheLayout.Section section, Object key, Object value) {
        String parent = switch (section.name()) {
            case "eventSignals", "snapshots", "evidence", "interpretations", "userStates",
                    "timelines", "timelineFingerprints", "researchLinks" -> "events";
            case "runSteps" -> "runs";
            default -> null;
        };
        Object parentId = key;
        if ("notifications".equals(section.name()) && value != null) {
            parent = "events";
            parentId = ((RadarEventWorkspace.Notification) value).getEventId();
        } else if ("agentRunsBySubject".equals(section.name()) && String.valueOf(key).startsWith("RADAR_EVENT:")) {
            parent = "events";
            parentId = Long.valueOf(String.valueOf(key).substring("RADAR_EVENT:".length()));
        }
        return parent == null ? "" : section(parent).key(RadarCacheLayout.prefix(generation), parentId);
    }

    private RadarCacheLayout.Section section(String name) {
        return java.util.Objects.requireNonNull(sections.get(name), "未知雷达缓存分区");
    }

    List<Change> changes() {
        List<Change> result = new ArrayList<>();
        for (RedisRadarRecordMap<?, ?> map : records.values()) {
            result.addAll(map.changes());
        }
        extendOwnedExpirations(result);
        return result;
    }

    private void extendOwnedExpirations(List<Change> changes) {
        List<Change> renewals = new ArrayList<>();
        Set<String> written = new java.util.HashSet<>();
        String prefix = RadarCacheLayout.prefix(generation);
        for (Change change : changes) {
            written.add(change.section().key(prefix, change.id()));
        }
        for (Change change : changes) {
            if (change.payload() == null || change.deadline() <= store.epochMillis(now)) {
                continue;
            }
            if ("events".equals(change.section().name())) {
                for (String name : RadarCacheLayout.EVENT_OWNED) {
                    addRenewal(renewals, written, section(name), change.id(), change.deadline());
                }
                addRenewal(renewals, written, section("agentRunsBySubject"), "RADAR_EVENT:" + change.id(), change.deadline());
                addRenewal(renewals, written, section("eventIdsByKey"),
                        getEvents().get(change.id()).getEventKey(), change.deadline());
            } else if ("signals".equals(change.section().name())) {
                addRenewal(renewals, written, section("signalIdsByItemId"),
                        getSignals().get(change.id()).getItemId(), change.deadline());
            }
        }
        changes.addAll(renewals);
    }

    private void addRenewal(List<Change> renewals, Set<String> written, RadarCacheLayout.Section section,
                            Object id, long deadline) {
        if (id != null && written.add(section.key(RadarCacheLayout.prefix(generation), id))) {
            renewals.add(new Change(section, id, deadline, RadarCacheLayout.KEEP_TTL, ""));
        }
    }

    static <K, V> void prefetch(Map<K, V> map, Collection<K> keys) {
        if (map instanceof RedisRadarRecordMap<K, V> redisMap) {
            redisMap.prefetch(keys);
        }
    }

    record Change(RadarCacheLayout.Section section, Object id, long deadline, String payload, String parentKey) {
    }

    private final class NotificationList extends AbstractList<RadarEventWorkspace.Notification> {
        private List<Long> ids;

        private List<Long> ids() {
            if (ids == null) {
                ids = new ArrayList<>(notifications.keySet());
            }
            return ids;
        }

        @Override
        public RadarEventWorkspace.Notification get(int index) {
            return notifications.get(ids().get(index));
        }

        @Override
        public int size() {
            return ids().size();
        }

        @Override
        public void add(int index, RadarEventWorkspace.Notification value) {
            ids().add(index, value.getId());
            notifications.put(value.getId(), value);
        }

        @Override
        public RadarEventWorkspace.Notification remove(int index) {
            return notifications.remove(ids().remove(index));
        }
    }
}
