package com.finscope.domain.industrychain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 产业链动态视图；新闻展示字段始终来自 Research Radar。 */
public class IndustryChainEventFeed {
    private Long chainId;
    private int hours;
    private LocalDateTime refreshedAt;
    private Map<String, Integer> nodeEventCounts = new LinkedHashMap<String, Integer>();
    private List<EventItem> events = new ArrayList<EventItem>();

    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public int getHours() { return hours; }
    public void setHours(int hours) { this.hours = hours; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(LocalDateTime refreshedAt) { this.refreshedAt = refreshedAt; }
    public Map<String, Integer> getNodeEventCounts() { return nodeEventCounts; }
    public void setNodeEventCounts(Map<String, Integer> nodeEventCounts) {
        this.nodeEventCounts = nodeEventCounts == null
                ? new LinkedHashMap<String, Integer>() : new LinkedHashMap<String, Integer>(nodeEventCounts);
    }
    public List<EventItem> getEvents() { return events; }
    public void setEvents(List<EventItem> events) {
        this.events = events == null ? new ArrayList<EventItem>() : new ArrayList<EventItem>(events);
    }

    public static class EventItem {
        private Long eventId;
        private String title;
        private String summary;
        private String categoryCode;
        private String status;
        private LocalDateTime firstSeenAt;
        private LocalDateTime lastSeenAt;
        private int sourceCount;
        private int signalCount;
        private int hotspotScore;
        private IndustryChainEventImpact impact;

        public Long getEventId() { return eventId; }
        public void setEventId(Long eventId) { this.eventId = eventId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getCategoryCode() { return categoryCode; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
        public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
        public LocalDateTime getLastSeenAt() { return lastSeenAt; }
        public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
        public int getSourceCount() { return sourceCount; }
        public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }
        public int getSignalCount() { return signalCount; }
        public void setSignalCount(int signalCount) { this.signalCount = signalCount; }
        public int getHotspotScore() { return hotspotScore; }
        public void setHotspotScore(int hotspotScore) { this.hotspotScore = hotspotScore; }
        public IndustryChainEventImpact getImpact() { return impact; }
        public void setImpact(IndustryChainEventImpact impact) { this.impact = impact; }
    }
}
