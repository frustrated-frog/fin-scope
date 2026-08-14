package com.finscope.domain.globalexpectations;

import lombok.Data;

import java.util.List;

@Data
public class GlobalExpectationHistorySnapshot {
    private String tokenId;
    private long fetchedAt;
    private List<GlobalExpectationHistoryPoint> points;
}
