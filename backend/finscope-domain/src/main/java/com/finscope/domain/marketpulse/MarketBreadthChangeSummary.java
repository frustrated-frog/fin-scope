package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class MarketBreadthChangeSummary {
    private LocalDate previousBusinessDate;
    private String headline;
    private Double advanceRatioChange;
    private Double medianChangePctChange;
    private Double totalAmountChangeRatio;
    private Double ma20RatioChange;
    private Integer newHighLowBalanceChange;
    private Integer netAdvancesChange;
    private List<String> changes = new ArrayList<>();
}
