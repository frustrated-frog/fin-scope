package com.finscope.domain.majorevent;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MajorEvent {
    private Long id;
    private String originType;
    private String originKey;
    private String title;
    private String summary;
    private String sourceName;
    private String sourceUrl;
    private String categoryCode;
    private LocalDate occurredDate;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
