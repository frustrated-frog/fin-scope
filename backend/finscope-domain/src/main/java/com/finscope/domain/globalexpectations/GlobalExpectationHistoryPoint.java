package com.finscope.domain.globalexpectations;

import lombok.Data;

@Data
public class GlobalExpectationHistoryPoint {
    private long timestamp;
    private double probability;
}
