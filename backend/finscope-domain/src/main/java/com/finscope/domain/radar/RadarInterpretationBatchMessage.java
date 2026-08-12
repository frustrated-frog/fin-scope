package com.finscope.domain.radar;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class RadarInterpretationBatchMessage {
    public static final int MAX_EVENT_COUNT = 20;

    private String runKey;
    private LocalDateTime completedAt;
    private List<Long> eventIds = new ArrayList<Long>();

    public RadarInterpretationBatchMessage() {
    }

    public RadarInterpretationBatchMessage(String runKey, LocalDateTime completedAt, List<Long> eventIds) {
        this.runKey = runKey;
        this.completedAt = completedAt;
        setEventIds(eventIds);
    }

    public String getRunKey() { return runKey; }
    public void setRunKey(String runKey) { this.runKey = runKey; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public List<Long> getEventIds() { return Collections.unmodifiableList(eventIds); }
    public void setEventIds(List<Long> values) {
        LinkedHashSet<Long> unique = new LinkedHashSet<Long>();
        if (values != null) {
            for (Long value : values) {
                if (value != null) unique.add(value);
                if (unique.size() >= MAX_EVENT_COUNT) break;
            }
        }
        eventIds = new ArrayList<Long>(unique);
    }
}
