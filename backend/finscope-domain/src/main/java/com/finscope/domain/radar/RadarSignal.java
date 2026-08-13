package com.finscope.domain.radar;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RadarSignal {
    private Long id;
    private String itemId;
    private String providerCode;
    private String sourceName;
    private String sourceTier;
    private String categoryCode;
    private String title;
    private String content;
    private String url;
    private LocalDateTime publishedAt;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private String contentHash;
    private String status;
    private Integer sourceRank;
    private Integer previousSourceRank;
    private double sourceWeight;
}
