package com.finscope.dao.radar;

import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventDetailSnapshot;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarSignalStatus;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RadarRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarSignal capture(RadarSignal signal, LocalDateTime now) {
        return store.update(state -> capture(state, signal, now));
    }

    /** 批量预取身份与信号，只提交实际发生变化的记录。 */
    public List<RadarSignal> captureBatch(List<RadarSignal> signals, LocalDateTime now) {
        if (signals == null || signals.isEmpty()) {
            return Collections.emptyList();
        }
        List<RadarSignal> result = new ArrayList<>();
        for (int start = 0; start < signals.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<RadarSignal> batch = signals.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, signals.size()));
            result.addAll(store.update(state -> {
                RedisRadarCacheStore.prefetch(state.getSignalIdsByItemId(), batch.stream().map(RadarSignal::getItemId).toList());
                List<Long> existingIds = batch.stream().map(signal -> state.getSignalIdsByItemId().get(signal.getItemId()))
                        .filter(java.util.Objects::nonNull).toList();
                RedisRadarCacheStore.prefetch(state.getSignals(), existingIds);
                List<RadarSignal> captured = new ArrayList<>();
                for (RadarSignal signal : batch) {
                    captured.add(capture(state, signal, now));
                }
                return captured;
            }));
        }
        return result;
    }

    private RadarSignal capture(RadarCacheState state, RadarSignal signal, LocalDateTime now) {
        Long id = state.getSignalIdsByItemId().get(signal.getItemId());
        RadarSignal existing = id == null ? null : state.getSignals().get(id);
        if (id == null) {
            id = store.stableId("signal", signal.getItemId());
        }
        signal.setId(id);
        signal.setFirstSeenAt(existing == null || existing.getFirstSeenAt() == null
                ? now : existing.getFirstSeenAt());
        signal.setLastSeenAt(now);
        signal.setStatus(RadarSignalStatus.ACTIVE.code());
        if (signal.getPreviousSourceRank() == null && existing != null) {
            signal.setPreviousSourceRank(existing.getSourceRank());
        }
        state.getSignals().put(id, signal);
        state.getSignalIdsByItemId().put(signal.getItemId(), id);
        return signal;
    }

    public List<RadarSignal> findActiveSignals(LocalDateTime since, int limit) {
        return store.read().getSignals().values().stream()
                .filter(value -> RadarSignalStatus.ACTIVE.code().equals(value.getStatus()))
                .filter(value -> value.getLastSeenAt() != null && !value.getLastSeenAt().isBefore(since))
                .sorted(signalOrder())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    public Optional<RadarSignal> findSignalByItemId(String itemId) {
        RadarCacheState state = store.read();
        Long id = state.getSignalIdsByItemId().get(itemId);
        return id == null ? Optional.empty() : Optional.ofNullable(state.getSignals().get(id));
    }

    public RadarEvent saveEvent(RadarEvent event) {
        boolean preserveEvidence = event.getEvidenceStatus() == null;
        return store.update(state -> saveEvent(state, event, preserveEvidence));
    }

    private RadarEvent saveEvent(RadarCacheState state, RadarEvent event, boolean preserveEvidence) {
        Long id = state.getEventIdsByKey().get(event.getEventKey());
        RadarEvent existing = id == null ? null : state.getEvents().get(id);
        if (id == null) {
            id = store.stableId("event", event.getEventKey());
        }
        event.setId(id);
        if (existing != null && existing.getFirstSeenAt() != null
                && (event.getFirstSeenAt() == null || existing.getFirstSeenAt().isBefore(event.getFirstSeenAt()))) {
            event.setFirstSeenAt(existing.getFirstSeenAt());
        }
        if (preserveEvidence) {
            preserveEnhancement(event, existing);
        }
        state.getEvents().put(id, event);
        state.getEventIdsByKey().put(event.getEventKey(), id);
        return event;
    }

    /** 每批最多 100 个事件，事件与信号关系在同一次原子提交中完成。 */
    public List<RadarEvent> saveEventsWithSignals(List<RadarEvent> events,
                                                  Map<String, List<RadarEventSignal>> linksByEventKey) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new java.util.HashSet<>();
        for (RadarEvent event : events) {
            if (event == null || blank(event.getEventKey()) || !unique.add(event.getEventKey())) {
                throw new IllegalArgumentException("事件批次包含空身份或重复身份");
            }
        }
        Set<String> preserveEvidence = events.stream().filter(event -> event.getEvidenceStatus() == null)
                .map(RadarEvent::getEventKey).collect(Collectors.toSet());
        Map<String, List<Long>> duplicates = duplicateCandidates(events);
        List<RadarEvent> result = new ArrayList<>();
        for (int start = 0; start < events.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<RadarEvent> batch = events.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, events.size()));
            result.addAll(store.update(state -> saveEventBatch(state, batch, linksByEventKey, duplicates, preserveEvidence)));
        }
        return result;
    }

    private Map<String, List<Long>> duplicateCandidates(List<RadarEvent> events) {
        Set<String> titles = events.stream().map(RadarEvent::getCanonicalTitle).filter(value -> !blank(value))
                .map(this::normalizedTitle).collect(Collectors.toSet());
        return store.query(state -> {
            RedisRadarCacheStore.prefetch(state.getEventIdsByKey(), events.stream().map(RadarEvent::getEventKey).toList());
            Map<String, List<Long>> result = state.getEvents().values().stream().filter(this::active)
                    .filter(event -> !blank(event.getCanonicalTitle()) && titles.contains(normalizedTitle(event.getCanonicalTitle())))
                    .collect(Collectors.groupingBy(event -> normalizedTitle(event.getCanonicalTitle()),
                            LinkedHashMap::new, Collectors.mapping(RadarEvent::getId, Collectors.toList())));
            for (RadarEvent event : events) {
                if (!blank(event.getCanonicalTitle())) {
                    Long id = state.getEventIdsByKey().getOrDefault(event.getEventKey(), store.stableId("event", event.getEventKey()));
                    result.computeIfAbsent(normalizedTitle(event.getCanonicalTitle()), ignored -> new ArrayList<>()).add(id);
                }
            }
            return result;
        });
    }

    private List<RadarEvent> saveEventBatch(RadarCacheState state, List<RadarEvent> events,
                                            Map<String, List<RadarEventSignal>> linksByEventKey,
                                            Map<String, List<Long>> duplicates, Set<String> preserveEvidence) {
        RedisRadarCacheStore.prefetch(state.getEventIdsByKey(), events.stream().map(RadarEvent::getEventKey).toList());
        List<Long> ids = events.stream().map(event -> state.getEventIdsByKey().getOrDefault(event.getEventKey(),
                store.stableId("event", event.getEventKey()))).toList();
        RedisRadarCacheStore.prefetch(state.getEvents(), ids);
        RedisRadarCacheStore.prefetch(state.getEventSignals(), ids);
        List<Long> duplicateIds = events.stream().map(RadarEvent::getCanonicalTitle).filter(value -> !blank(value))
                .flatMap(title -> duplicates.getOrDefault(normalizedTitle(title), Collections.emptyList()).stream()).distinct().toList();
        RedisRadarCacheStore.prefetch(state.getEvents(), duplicateIds);
        List<RadarEvent> result = new ArrayList<>();
        for (RadarEvent event : events) {
            RadarEvent saved = saveEvent(state, event, preserveEvidence.contains(event.getEventKey()));
            List<RadarEventSignal> links = linksByEventKey == null ? Collections.emptyList()
                    : linksByEventKey.getOrDefault(event.getEventKey(), Collections.emptyList());
            replaceEventSignals(state, saved.getId(), links);
            if (!blank(saved.getCanonicalTitle())) {
                String title = normalizedTitle(saved.getCanonicalTitle());
                List<Long> candidates = new ArrayList<>(duplicates.getOrDefault(title, Collections.emptyList()));
                for (Long duplicateId : candidates) {
                    RadarEvent previous = state.getEvents().get(duplicateId);
                    if (previous != null && !duplicateId.equals(saved.getId()) && active(previous)
                            && !blank(previous.getCanonicalTitle()) && title.equals(normalizedTitle(previous.getCanonicalTitle()))) {
                        previous.setStatus("EXPIRED");
                        previous.setUpdatedAt(saved.getUpdatedAt());
                    }
                }
            }
            result.add(saved);
        }
        return result;
    }

    private String normalizedTitle(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }

    public void updateEvidenceEnhancement(RadarEvent event) {
        store.update(state -> {
            if (event != null && event.getId() != null && state.getEvents().containsKey(event.getId())) {
                state.getEvents().put(event.getId(), event);
            }
            return null;
        });
    }

    public void expireEventsExcept(Set<String> activeEventKeys, LocalDateTime windowStart, LocalDateTime now) {
        store.update(state -> {
            Set<String> active = activeEventKeys == null ? Collections.emptySet() : activeEventKeys;
            for (RadarEvent event : state.getEvents().values()) {
                if (active.contains(event.getEventKey())) {
                    continue;
                }
                LocalDateTime seenAt = event.getLastSeenAt() == null ? event.getUpdatedAt() : event.getLastSeenAt();
                String status = seenAt != null && !seenAt.isBefore(windowStart) ? "QUIET" : "EXPIRED";
                if (!status.equals(event.getStatus())) {
                    event.setStatus(status);
                    event.setUpdatedAt(now);
                }
            }
            return null;
        });
    }

    public void replaceEventSignals(Long eventId, List<RadarEventSignal> links) {
        store.update(state -> {
            replaceEventSignals(state, eventId, links);
            return null;
        });
    }

    private void replaceEventSignals(RadarCacheState state, Long eventId, List<RadarEventSignal> links) {
        List<RadarEventSignal> values = new ArrayList<>();
        if (links != null) {
            RedisRadarCacheStore.prefetch(state.getSignals(), links.stream().map(RadarEventSignal::getSignalId).toList());
            for (RadarEventSignal link : links) {
                if (state.getSignals().containsKey(link.getSignalId())) {
                    link.setEventId(eventId);
                    values.add(link);
                }
            }
        }
        state.getEventSignals().put(eventId, values);
    }

    public Optional<RadarEvent> findEvent(Long id) {
        return Optional.ofNullable(store.read().getEvents().get(id));
    }

    public Optional<RadarEventDetailSnapshot> findDetailSnapshot(Long eventId) {
        return store.query(state -> detailSnapshot(state, eventId));
    }

    private Optional<RadarEventDetailSnapshot> detailSnapshot(RadarCacheState state, Long eventId) {
        RadarEvent event = state.getEvents().get(eventId);
        if (event == null) {
            return Optional.empty();
        }
        List<RadarEventInterpretation> interpretations = state.getInterpretations()
                .getOrDefault(eventId, Collections.emptyList());
        return Optional.of(RadarEventDetailSnapshot.builder()
                .event(event)
                .signals(signalsForEvent(state, eventId))
                .signalLinks(state.getEventSignals().getOrDefault(eventId, Collections.emptyList()))
                .evidence(RadarEvidenceRepository.evidenceForEvent(state, eventId))
                .interpretations(interpretations)
                .latestInterpretation(RadarEventInterpretationRepository.latest(interpretations).orElse(null))
                .workspaceState(state.getUserStates().get(eventId))
                .researchLinks(RadarEventWorkspaceRepository.researchLinksForEvent(state, eventId))
                .agentRuns(state.getAgentRunsBySubject().getOrDefault("RADAR_EVENT:" + eventId, Collections.emptyList()))
                .build());
    }

    public Optional<RadarEvent> findEventByKey(String eventKey) {
        return Optional.ofNullable(findEventsByKeys(Collections.singletonList(eventKey)).get(eventKey));
    }

    public Map<String, RadarEvent> findEventsByKeys(Collection<String> eventKeys) {
        if (eventKeys == null || eventKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        return store.query(state -> {
            RedisRadarCacheStore.prefetch(state.getEventIdsByKey(), eventKeys);
            Map<String, RadarEvent> result = new LinkedHashMap<>();
            for (String eventKey : eventKeys) {
                Long id = state.getEventIdsByKey().get(eventKey);
                RadarEvent event = id == null ? null : state.getEvents().get(id);
                if (event != null) {
                    result.put(eventKey, event);
                }
            }
            return result;
        });
    }

    public List<RadarEvent> findEventsSince(LocalDateTime since, int limit) {
        return activeEvents().stream()
                .filter(value -> eventTime(value) != null && !eventTime(value).isBefore(since))
                .sorted(eventTimeOrder())
                .limit(Math.max(1, Math.min(limit, 500)))
                .collect(Collectors.toList());
    }

    public List<RadarEvent> findEventsBetween(LocalDateTime since, LocalDateTime before, int limit) {
        return activeEvents().stream()
                .filter(value -> eventTime(value) != null && !eventTime(value).isBefore(since)
                        && eventTime(value).isBefore(before))
                .sorted(eventTimeOrder())
                .limit(Math.max(1, Math.min(limit, 500)))
                .collect(Collectors.toList());
    }

    public List<RadarSignal> findSignalsByEventId(Long eventId) {
        return signalsForEvent(store.read(), eventId);
    }

    private List<RadarSignal> signalsForEvent(RadarCacheState state, Long eventId) {
        List<RadarSignal> values = new ArrayList<RadarSignal>();
        List<RadarEventSignal> links = state.getEventSignals().getOrDefault(eventId, Collections.emptyList());
        RedisRadarCacheStore.prefetch(state.getSignals(), links.stream().map(RadarEventSignal::getSignalId).toList());
        for (RadarEventSignal link : links) {
            RadarSignal signal = state.getSignals().get(link.getSignalId());
            if (signal != null) {
                values.add(signal);
            }
        }
        values.sort(signalOrder());
        return values;
    }

    public List<RadarEventSignal> findEventSignals(Long eventId) {
        return new ArrayList<RadarEventSignal>(store.read().getEventSignals()
                .getOrDefault(eventId, Collections.emptyList()));
    }

    public List<AgentRun> findAgentRunsBySubject(String subjectType, Long subjectId) {
        if (subjectType == null || subjectId == null) {
            return Collections.emptyList();
        }
        return new ArrayList<AgentRun>(store.read().getAgentRunsBySubject()
                .getOrDefault(subjectType + ':' + subjectId, Collections.emptyList()));
    }

    public List<RadarEvent> findRanked(String category, boolean watchlistOnly, int limit) {
        return findWorkspaceEvents(category, watchlistOnly).stream()
                .limit(normalizeLimit(limit)).collect(Collectors.toList());
    }

    /** 只读取当前 TTL 窗口的事件集合；由上层先筛选用户状态再截断。 */
    public List<RadarEvent> findWorkspaceEvents(String category, boolean watchlistOnly) {
        return activeEvents().stream()
                .filter(value -> blank(category) || "ALL".equalsIgnoreCase(category)
                        || category.trim().equalsIgnoreCase(value.getCategoryCode()))
                .filter(value -> !watchlistOnly || value.getWatchlistRelevance() > 0)
                .sorted(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                        .thenComparing(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed())
                        .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RadarEvent::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    public List<RadarEvent> findTopByDashboardCategory(String dashboardCategory, int limit) {
        return activeEvents().stream()
                .filter(value -> dashboardCategory != null && dashboardCategory.equals(value.getDashboardCategory()))
                .sorted(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed()
                        .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RadarEvent::getId, Comparator.reverseOrder()))
                .limit(normalizeLimit(limit))
                .collect(Collectors.toList());
    }

    public List<RadarEvent> findObservationCandidates(int limit) {
        return activeEvents().stream()
                .sorted(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed()
                        .thenComparing(Comparator.comparingInt(RadarEvent::getConfidenceScore).reversed())
                        .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(normalizeLimit(limit))
                .collect(Collectors.toList());
    }

    public List<RadarEvent> findEventsMissingDashboardCategory(int limit) {
        return activeEvents().stream()
                .filter(value -> blank(value.getDashboardCategory()) || "UNCLASSIFIED".equals(value.getDashboardCategory()))
                .sorted(eventTimeOrder())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .collect(Collectors.toList());
    }

    public List<RadarEvent> findEventsForDashboardClassification(int limit) {
        return activeEvents().stream().sorted(eventTimeOrder())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .collect(Collectors.toList());
    }

    public void expireDuplicateEventsByCanonicalTitle(String canonicalTitle, Long keepEventId, LocalDateTime now) {
        if (blank(canonicalTitle) || keepEventId == null) {
            return;
        }
        store.update(state -> {
            for (RadarEvent event : state.getEvents().values()) {
                if (!keepEventId.equals(event.getId()) && active(event)
                        && canonicalTitle.trim().equalsIgnoreCase(event.getCanonicalTitle().trim())) {
                    event.setStatus("EXPIRED");
                    event.setUpdatedAt(now);
                }
            }
            return null;
        });
    }

    public void updateDashboardCategory(Long eventId, String dashboardCategory) {
        store.update(state -> {
            RadarEvent event = state.getEvents().get(eventId);
            if (event != null) {
                event.setDashboardCategory(dashboardCategory);
            }
            return null;
        });
    }

    public void updateDashboardCategories(Map<Long, String> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(categories.keySet());
        for (int start = 0; start < ids.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, ids.size()));
            store.update(state -> {
                RedisRadarCacheStore.prefetch(state.getEvents(), batch);
                for (Long id : batch) {
                    RadarEvent event = state.getEvents().get(id);
                    if (event != null) {
                        event.setDashboardCategory(categories.get(id));
                    }
                }
                return null;
            });
        }
    }

    public void expireSignals(LocalDateTime before, LocalDateTime now) {
        store.update(state -> {
            for (RadarSignal signal : state.getSignals().values()) {
                if (RadarSignalStatus.ACTIVE.code().equals(signal.getStatus()) && signal.getLastSeenAt() != null
                        && signal.getLastSeenAt().isBefore(before)) {
                    signal.setStatus(RadarSignalStatus.EXPIRED.code());
                }
            }
            return null;
        });
    }

    private List<RadarEvent> activeEvents() {
        return store.read().getEvents().values().stream().filter(this::active).collect(Collectors.toList());
    }

    private boolean active(RadarEvent value) {
        return value != null && ("ACTIVE".equals(value.getStatus()) || "QUIET".equals(value.getStatus()));
    }

    private LocalDateTime eventTime(RadarEvent value) {
        return value.getLastSeenAt() == null ? value.getUpdatedAt() : value.getLastSeenAt();
    }

    private Comparator<RadarEvent> eventTimeOrder() {
        return Comparator.comparing(this::eventTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarEvent::getId, Comparator.reverseOrder());
    }

    private Comparator<RadarSignal> signalOrder() {
        return Comparator.comparing((RadarSignal value) -> value.getPublishedAt() == null
                        ? value.getFirstSeenAt() : value.getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarSignal::getId, Comparator.reverseOrder());
    }

    private void preserveEnhancement(RadarEvent event, RadarEvent existing) {
        if (existing == null) {
            return;
        }
        event.setEvidenceStatus(existing.getEvidenceStatus());
        event.setEvidenceSummary(existing.getEvidenceSummary());
        event.setEvidenceWarning(existing.getEvidenceWarning());
        event.setEvidenceFingerprint(existing.getEvidenceFingerprint());
        event.setEvidenceCount(existing.getEvidenceCount());
        event.setEvidenceSourceCount(existing.getEvidenceSourceCount());
        event.setEvidenceUpdatedAt(existing.getEvidenceUpdatedAt());
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
