package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 归因增量研究的独立快照，不覆盖原报告内容。 */
@Data
public class AttributionResearchInsights {
    private String asOfDate;
    private String generatedAt;
    private com.finscope.common.enums.attribution.AttributionInsightStatus status;
    private List<AttributionBusinessLink> businesses = new ArrayList<>();
    private List<AttributionExpectationChange> expectations = new ArrayList<>();
    private List<AttributionComparisonRow> comparisons = new ArrayList<>();
    private String comparisonSummary;
    private List<AttributionEventSource> sources = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
