package com.finscope.domain.radar;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class RadarTimelineEntryDraft {
    String fingerprint;
    String eventType;
    String title;
    String summary;
    String referenceType;
    Long referenceId;
    LocalDateTime occurredAt;
}
