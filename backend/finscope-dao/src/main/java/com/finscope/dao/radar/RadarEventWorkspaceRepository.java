package com.finscope.dao.radar;

import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Repository
public class RadarEventWorkspaceRepository {
    private static final List<String> DISPOSITIONS = Arrays.asList("ACTIVE", "LATER", "IGNORED");

    @Resource
    private RedisRadarCacheStore store;

    public RadarEventWorkspace.State updateState(Long eventId, boolean markRead, String disposition,
                                                  Boolean followed, String fingerprint) {
        requireEventId(eventId);
        if (disposition != null && !DISPOSITIONS.contains(disposition)) {
            throw new BusinessException(BizErrorCode.RADAR_EVENT_STATE_INVALID);
        }
        return store.update(state -> {
            RadarEventWorkspace.State value = state.getUserStates().get(eventId);
            if (value == null) {
                value = defaultState(eventId);
            }
            LocalDateTime now = LocalDateTime.now();
            if (markRead) {
                value.setRead(true);
                value.setReadAt(now);
            }
            if (disposition != null) {
                value.setDisposition(disposition);
            }
            if (followed != null) {
                value.setFollowed(followed);
            }
            if (fingerprint != null) {
                value.setLastViewedFingerprint(fingerprint);
            }
            value.setUpdatedAt(now);
            state.getUserStates().put(eventId, value);
            return value;
        });
    }

    public RadarEventWorkspace.State findState(Long eventId) {
        RadarEventWorkspace.State value = store.read().getUserStates().get(eventId);
        return value == null ? defaultState(eventId) : value;
    }

    public Map<Long, RadarEventWorkspace.Summary> findSummaries(List<Long> eventIds) {
        Map<Long, RadarEventWorkspace.Summary> result = new LinkedHashMap<Long, RadarEventWorkspace.Summary>();
        if (eventIds == null || eventIds.isEmpty()) {
            return result;
        }
        RadarCacheState state = store.read();
        for (Long eventId : eventIds) {
            RadarEventWorkspace.Summary summary = summary(state.getUserStates().get(eventId), eventId);
            summary.setResearchRunCount(state.getResearchLinks().getOrDefault(eventId, Collections.emptyList()).size());
            int unread = 0;
            for (RadarEventWorkspace.Notification notification : state.getNotifications()) {
                if (Objects.equals(eventId, notification.getEventId()) && !notification.isRead()) {
                    unread++;
                }
            }
            summary.setUnreadNotificationCount(unread);
            result.put(eventId, summary);
        }
        return result;
    }

    public List<Long> findFollowedEventIds(int limit) {
        RadarCacheState state = store.read();
        return state.getUserStates().values().stream()
                .filter(RadarEventWorkspace.State::isFollowed)
                .filter(value -> !"IGNORED".equals(value.getDisposition()))
                .filter(value -> active(state.getEvents().get(value.getEventId())))
                .sorted(Comparator.comparing(RadarEventWorkspace.State::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(RadarEventWorkspace.State::getEventId)
                .limit(Math.max(1, Math.min(limit, 50)))
                .collect(Collectors.toList());
    }

    /** 雷达是临时流，不再承载可长期维护的观察项。 */
    public List<RadarEventWorkspace.Observation> ensureDefaultObservation(Long eventId, String content) {
        return Collections.emptyList();
    }

    public RadarEventWorkspace.Observation addObservation(Long eventId, String content) {
        throw new BusinessException(BizErrorCode.RADAR_OBSERVATION_NOT_FOUND);
    }

    public RadarEventWorkspace.Observation setObservationStatus(Long eventId, Long observationId, String status) {
        throw new BusinessException(BizErrorCode.RADAR_OBSERVATION_NOT_FOUND);
    }

    public void deleteObservation(Long eventId, Long observationId) {
        throw new BusinessException(BizErrorCode.RADAR_OBSERVATION_NOT_FOUND);
    }

    public List<RadarEventWorkspace.Observation> findObservations(Long eventId) {
        return Collections.emptyList();
    }

    public void appendTimeline(Long eventId, String eventFingerprint, String eventType, String title,
                               String summary, String referenceType, Long referenceId, LocalDateTime occurredAt) {
        requireEventId(eventId);
        store.update(state -> {
            List<RadarEventWorkspace.TimelineEntry> values = state.getTimelines()
                    .computeIfAbsent(eventId, ignored -> new ArrayList<RadarEventWorkspace.TimelineEntry>());
            Set<String> fingerprints = state.getTimelineFingerprints()
                    .computeIfAbsent(eventId, ignored -> new LinkedHashSet<String>());
            String key = eventFingerprint + ':' + eventType + ':' + referenceType + ':' + referenceId;
            if (fingerprints.add(key)) {
                RadarEventWorkspace.TimelineEntry value = new RadarEventWorkspace.TimelineEntry();
                value.setId(state.nextSequence());
                value.setEventId(eventId);
                value.setEventType(eventType);
                value.setTitle(title);
                value.setSummary(summary);
                value.setReferenceType(referenceType);
                value.setReferenceId(referenceId);
                value.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
                values.add(value);
            }
            return null;
        });
    }

    public List<RadarEventWorkspace.TimelineEntry> findTimeline(Long eventId) {
        List<RadarEventWorkspace.TimelineEntry> values = new ArrayList<RadarEventWorkspace.TimelineEntry>(
                store.read().getTimelines().getOrDefault(eventId, Collections.emptyList()));
        values.sort(Comparator.comparing(RadarEventWorkspace.TimelineEntry::getOccurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return values;
    }

    public RadarEventWorkspace.ResearchLink linkResearchRun(Long eventId, Long researchRunId, String questionSnapshot) {
        requireEventId(eventId);
        return store.update(state -> {
            List<RadarEventWorkspace.ResearchLink> values = state.getResearchLinks()
                    .computeIfAbsent(eventId, ignored -> new ArrayList<RadarEventWorkspace.ResearchLink>());
            for (RadarEventWorkspace.ResearchLink existing : values) {
                if (Objects.equals(existing.getResearchRunId(), researchRunId)) {
                    return existing;
                }
            }
            RadarEventWorkspace.ResearchLink value = new RadarEventWorkspace.ResearchLink();
            value.setId(state.nextSequence());
            value.setEventId(eventId);
            value.setResearchRunId(researchRunId);
            value.setQuestionSnapshot(questionSnapshot == null ? null : questionSnapshot.trim());
            value.setCreatedAt(LocalDateTime.now());
            values.add(value);
            return value;
        });
    }

    public List<RadarEventWorkspace.ResearchLink> findResearchLinks(Long eventId) {
        List<RadarEventWorkspace.ResearchLink> values = new ArrayList<RadarEventWorkspace.ResearchLink>(
                store.read().getResearchLinks().getOrDefault(eventId, Collections.emptyList()));
        values.sort(Comparator.comparing(RadarEventWorkspace.ResearchLink::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return values;
    }

    public boolean createNotification(Long eventId, String notificationType, String fingerprint,
                                      String title, String message) {
        return store.update(state -> {
            String key = notificationType + ':' + fingerprint;
            if (!state.getNotificationFingerprints().add(key)) {
                return false;
            }
            RadarEventWorkspace.Notification value = new RadarEventWorkspace.Notification();
            value.setId(state.nextSequence());
            value.setEventId(eventId);
            value.setNotificationType(notificationType);
            value.setTitle(title);
            value.setMessage(message);
            value.setCreatedAt(LocalDateTime.now());
            state.getNotifications().add(value);
            return true;
        });
    }

    public List<RadarEventWorkspace.Notification> findNotifications(int limit) {
        List<RadarEventWorkspace.Notification> values = new ArrayList<RadarEventWorkspace.Notification>(store.read().getNotifications());
        values.sort(Comparator.comparing(RadarEventWorkspace.Notification::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return values.stream().limit(Math.max(1, Math.min(limit, 100))).collect(Collectors.toList());
    }

    public int countUnreadNotifications() {
        return (int) store.read().getNotifications().stream().filter(value -> !value.isRead()).count();
    }

    public int countNotificationsOn(LocalDate date) {
        return (int) store.read().getNotifications().stream()
                .filter(value -> value.getCreatedAt() != null && date.equals(value.getCreatedAt().toLocalDate()))
                .count();
    }

    public int countNewEventsOn(LocalDate date) {
        return (int) store.read().getEvents().values().stream()
                .filter(value -> value.getFirstSeenAt() != null && date.equals(value.getFirstSeenAt().toLocalDate()))
                .count();
    }

    public int countFollowedChangesOn(LocalDate date) {
        return (int) store.read().getNotifications().stream()
                .filter(value -> "FOLLOWED_EVENT_CHANGED".equals(value.getNotificationType()))
                .filter(value -> value.getCreatedAt() != null && date.equals(value.getCreatedAt().toLocalDate()))
                .count();
    }

    public int countOpenObservations() {
        return 0;
    }

    public void markNotificationRead(Long id) {
        store.update(state -> {
            for (RadarEventWorkspace.Notification value : state.getNotifications()) {
                if (Objects.equals(value.getId(), id) && value.getReadAt() == null) {
                    value.setReadAt(LocalDateTime.now());
                }
            }
            return null;
        });
    }

    public void markAllNotificationsRead() {
        store.update(state -> {
            LocalDateTime now = LocalDateTime.now();
            for (RadarEventWorkspace.Notification value : state.getNotifications()) {
                if (value.getReadAt() == null) {
                    value.setReadAt(now);
                }
            }
            return null;
        });
    }

    private RadarEventWorkspace.State defaultState(Long eventId) {
        RadarEventWorkspace.State value = new RadarEventWorkspace.State();
        value.setEventId(eventId);
        return value;
    }

    private RadarEventWorkspace.Summary summary(RadarEventWorkspace.State state, Long eventId) {
        RadarEventWorkspace.Summary value = new RadarEventWorkspace.Summary();
        value.setEventId(eventId);
        if (state != null) {
            value.setRead(state.isRead());
            value.setReadAt(state.getReadAt());
            value.setFollowed(state.isFollowed());
            value.setDisposition(state.getDisposition());
            value.setLastViewedFingerprint(state.getLastViewedFingerprint());
            value.setUpdatedAt(state.getUpdatedAt());
        }
        return value;
    }

    private boolean active(RadarEvent event) {
        return event != null && ("ACTIVE".equals(event.getStatus()) || "QUIET".equals(event.getStatus()));
    }

    private void requireEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException(BizErrorCode.RADAR_EVENT_ID_REQUIRED);
        }
    }
}
