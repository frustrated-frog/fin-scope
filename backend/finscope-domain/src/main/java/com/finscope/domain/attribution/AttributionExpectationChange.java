package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 归因增量研究的独立快照，不覆盖原报告内容。 */
@Data
public class AttributionExpectationChange {
    private String topic;
    private String priorExpectation;
    private String expectationBasis;
    private String newInformation;
    private String repricingPath;
    private String realized;
    private String nextCatalyst;
    private com.finscope.common.enums.attribution.NewsImpactDirection direction;
    private List<String> sourceIds = new ArrayList<>();
}
