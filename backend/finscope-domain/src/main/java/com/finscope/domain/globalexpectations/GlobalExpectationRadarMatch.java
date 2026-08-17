package com.finscope.domain.globalexpectations;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GlobalExpectationRadarMatch {
    private Long eventId;
    private String title;
    private String summary;
    private Integer matchScore;
    private Integer newsCount1h;
    private Integer newsCountPrevious1h;
    private Integer newsCount24h;
    private Integer independentSourceCount;
    private LocalDateTime lastSeenAt;
}
