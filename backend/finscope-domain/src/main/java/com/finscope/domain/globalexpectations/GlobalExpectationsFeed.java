package com.finscope.domain.globalexpectations;

import lombok.Data;

import java.util.List;

@Data
public class GlobalExpectationsFeed {
    private Integer marketCount;
    private Integer eventCount;
    private Integer signalCount;
    private String generatedAt;
    private List<GlobalExpectationEventGroup> groups;
}
