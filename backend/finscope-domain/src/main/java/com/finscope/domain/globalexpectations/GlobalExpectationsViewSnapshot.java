package com.finscope.domain.globalexpectations;

import lombok.Data;

import java.util.List;

@Data
public class GlobalExpectationsViewSnapshot {
    private long fetchedAt;
    private List<GlobalExpectationItem> items;
}
