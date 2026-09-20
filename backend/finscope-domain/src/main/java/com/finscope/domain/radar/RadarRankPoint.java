package com.finscope.domain.radar;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RadarRankPoint {
    private String eventKey;
    private LocalDateTime observedAt;
    private int rankPosition;
    private int hotspotScore;
    private int reportCount;
    private int sourceCount;
}
