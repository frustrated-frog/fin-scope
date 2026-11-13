package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalComparisonState;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class FundamentalWatchComparison {
    private FundamentalComparisonState state;
    private String message;
    private String token;
    private boolean reviewed;
    private FundamentalFinancialBaseline baseline;
    private FundamentalFinancialBaseline current;
    private List<FundamentalMetricChange> changes = new ArrayList<>();
}
