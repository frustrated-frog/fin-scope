package com.finscope.domain.radar;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RadarEventWorkspace {
    private RadarEventWorkspace() {}

    public static class State {
        private Long eventId;
        private LocalDateTime readAt;
        private Boolean read;
        private boolean followed;
        private String disposition = "ACTIVE";
        private String lastViewedFingerprint;
        private LocalDateTime updatedAt;
        public Long getEventId() { return eventId; }
        public void setEventId(Long value) { eventId = value; }
        public LocalDateTime getReadAt() { return readAt; }
        public void setReadAt(LocalDateTime value) { readAt = value; }
        public boolean isRead() { return read == null ? readAt != null : read; }
        public void setRead(boolean value) { read = value; }
        public boolean isFollowed() { return followed; }
        public void setFollowed(boolean value) { followed = value; }
        public String getDisposition() { return disposition; }
        public void setDisposition(String value) { disposition = value; }
        public String getLastViewedFingerprint() { return lastViewedFingerprint; }
        public void setLastViewedFingerprint(String value) { lastViewedFingerprint = value; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    }

    public static class Summary extends State {
        private int observationCount;
        private int openObservationCount;
        private int researchRunCount;
        private int unreadNotificationCount;
        public int getObservationCount() { return observationCount; }
        public void setObservationCount(int value) { observationCount = value; }
        public int getOpenObservationCount() { return openObservationCount; }
        public void setOpenObservationCount(int value) { openObservationCount = value; }
        public int getResearchRunCount() { return researchRunCount; }
        public void setResearchRunCount(int value) { researchRunCount = value; }
        public int getUnreadNotificationCount() { return unreadNotificationCount; }
        public void setUnreadNotificationCount(int value) { unreadNotificationCount = value; }
    }

    public static class Observation {
        private Long id;
        private Long eventId;
        private String content;
        private String status;
        private String source;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private LocalDateTime updatedAt;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public Long getEventId() { return eventId; }
        public void setEventId(Long value) { eventId = value; }
        public String getContent() { return content; }
        public void setContent(String value) { content = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getSource() { return source; }
        public void setSource(String value) { source = value; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime value) { createdAt = value; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime value) { completedAt = value; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    }

    public static class TimelineEntry {
        private Long id;
        private Long eventId;
        private String eventType;
        private String title;
        private String summary;
        private String referenceType;
        private Long referenceId;
        private LocalDateTime occurredAt;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public Long getEventId() { return eventId; }
        public void setEventId(Long value) { eventId = value; }
        public String getEventType() { return eventType; }
        public void setEventType(String value) { eventType = value; }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getSummary() { return summary; }
        public void setSummary(String value) { summary = value; }
        public String getReferenceType() { return referenceType; }
        public void setReferenceType(String value) { referenceType = value; }
        public Long getReferenceId() { return referenceId; }
        public void setReferenceId(Long value) { referenceId = value; }
        public LocalDateTime getOccurredAt() { return occurredAt; }
        public void setOccurredAt(LocalDateTime value) { occurredAt = value; }
    }

    public static class ResearchLink {
        private Long id;
        private Long eventId;
        private Long researchRunId;
        private String questionSnapshot;
        private String status;
        private String summary;
        private LocalDateTime createdAt;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public Long getEventId() { return eventId; }
        public void setEventId(Long value) { eventId = value; }
        public Long getResearchRunId() { return researchRunId; }
        public void setResearchRunId(Long value) { researchRunId = value; }
        public String getQuestionSnapshot() { return questionSnapshot; }
        public void setQuestionSnapshot(String value) { questionSnapshot = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getSummary() { return summary; }
        public void setSummary(String value) { summary = value; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    }

    public static class Notification {
        private Long id;
        private Long eventId;
        private String notificationType;
        private String title;
        private String message;
        private LocalDateTime readAt;
        private LocalDateTime createdAt;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public Long getEventId() { return eventId; }
        public void setEventId(Long value) { eventId = value; }
        public String getNotificationType() { return notificationType; }
        public void setNotificationType(String value) { notificationType = value; }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getMessage() { return message; }
        public void setMessage(String value) { message = value; }
        public LocalDateTime getReadAt() { return readAt; }
        public void setReadAt(LocalDateTime value) { readAt = value; }
        public boolean isRead() { return readAt != null; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    }

    public static class Trust {
        private int independentSourceCount;
        private Map<String, Integer> sourceTierCounts = new LinkedHashMap<String, Integer>();
        private int citationCoveredCount;
        private int citationTotalCount;
        private String concentration;
        private List<String> conflicts = new ArrayList<String>();
        private String limitation = "仅基于当前已收集证据";
        public int getIndependentSourceCount() { return independentSourceCount; }
        public void setIndependentSourceCount(int value) { independentSourceCount = value; }
        public Map<String, Integer> getSourceTierCounts() { return Collections.unmodifiableMap(sourceTierCounts); }
        public void setSourceTierCounts(Map<String, Integer> value) { sourceTierCounts = value == null ? new LinkedHashMap<String, Integer>() : new LinkedHashMap<String, Integer>(value); }
        public int getCitationCoveredCount() { return citationCoveredCount; }
        public void setCitationCoveredCount(int value) { citationCoveredCount = value; }
        public int getCitationTotalCount() { return citationTotalCount; }
        public void setCitationTotalCount(int value) { citationTotalCount = value; }
        public String getConcentration() { return concentration; }
        public void setConcentration(String value) { concentration = value; }
        public List<String> getConflicts() { return Collections.unmodifiableList(conflicts); }
        public void setConflicts(List<String> value) { conflicts = value == null ? new ArrayList<String>() : new ArrayList<String>(value); }
        public String getLimitation() { return limitation; }
        public void setLimitation(String value) { limitation = value; }
    }
}
