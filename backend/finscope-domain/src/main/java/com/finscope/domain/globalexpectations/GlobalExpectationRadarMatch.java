package com.finscope.domain.globalexpectations;

import lombok.Data;

@Data
public class GlobalExpectationRadarMatch {
    private Long eventId;
    private String title;
    private String summary;
    private Integer matchScore;
}
