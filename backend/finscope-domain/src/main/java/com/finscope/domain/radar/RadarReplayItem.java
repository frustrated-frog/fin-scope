package com.finscope.domain.radar;

import com.finscope.common.enums.radar.RadarReplayChange;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RadarReplayItem {
    private String eventId;
    private String eventKey;
    private String title;
    private String summary;
    private String category;
    private LocalDateTime observedAt;
    private Integer rank;
    private Integer hotspotScore;
    private Integer sourceCount;
    private Integer reportCount;
    private String lifecycle;
    private RadarReplayChange change;
    private Integer rankDelta;
}
