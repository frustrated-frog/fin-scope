package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReactionComparisonGroup {
    private String criteria;
    private boolean relaxed;
    private int eventCount;
    private int sampleCount;
    private int completeCount;
    private int notDueCount;
    private int missingCount;
    private BigDecimal median;
    private BigDecimal lowerQuartile;
    private BigDecimal upperQuartile;
    private List<ReactionComparableCase> cases = new ArrayList<>();
}
