package com.finscope.dao.radar;

import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarSignalStatus;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RadarRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarSignal capture(RadarSignal signal, LocalDateTime now) {
        return store.update(state -> {
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
        });
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
        return store.update(state -> {
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
            preserveEnhancement(event, existing);
            state.getEvents().put(id, event);
            state.getEventIdsByKey().put(event.getEventKey(), id);
            return event;
        });
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
                event.setStatus(seenAt != null && !seenAt.isBefore(windowStart) ? "QUIET" : "EXPIRED");
                event.setUpdatedAt(now);
            }
            return null;
        });
    }

    public void replaceEventSignals(Long eventId, List<RadarEventSignal> links) {
        store.update(state -> {
            List<RadarEventSignal> values = new ArrayList<RadarEventSignal>();
            if (links != null) {
                for (RadarEventSignal link : links) {
                    link.setEventId(eventId);
                    values.add(link);
                }
            }
            state.getEventSignals().put(eventId, values);
            return null;
        });
    }

    public Optional<RadarEvent> findEvent(Long id) {
        return Optional.ofNullable(store.read().getEvents().get(id));
    }

    public Optional<RadarEvent> findEventByKey(String eventKey) {
        RadarCacheState state = store.read();
        Long id = state.getEventIdsByKey().get(eventKey);
        return id == null ? Optional.empty() : Optional.ofNullable(state.getEvents().get(id));
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
        RadarCacheState state = store.read();
        List<RadarSignal> values = new ArrayList<RadarSignal>();
        for (RadarEventSignal link : state.getEventSignals().getOrDefault(eventId, Collections.emptyList())) {
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
        return activeEvents().stream()
                .filter(value -> blank(category) || "ALL".equalsIgnoreCase(category)
                        || category.trim().equalsIgnoreCase(value.getCategoryCode()))
                .filter(value -> !watchlistOnly || value.getWatchlistRelevance() > 0)
                .sorted(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                        .thenComparing(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed())
                        .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RadarEvent::getId, Comparator.reverseOrder()))
                .limit(normalizeLimit(limit))
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
        if (existing == null || event.getEvidenceStatus() != null) {
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
