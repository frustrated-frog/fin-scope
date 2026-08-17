package com.finscope.domain.globalexpectations;

import lombok.Data;

import java.util.List;

@Data
public class GlobalExpectationEventGroup {
    private String id;
    private String title;
    private List<String> themes;
    private String status;
    private Integer signalScore;
    private List<String> signalReasons;
    private Double volume24h;
    private List<GlobalExpectationItem> markets;
    private List<GlobalExpectationRadarMatch> radarMatches;
    private String realityDataStatus;
    private Integer expectationScore;
    private Integer realityScore;
    private String expectationRealityState;
    private List<String> gapReasons;
    private Integer newsCount1h;
    private Integer newsCount24h;
    private Integer independentSourceCount;
    private GlobalExpectationInterpretation interpretation;
}
