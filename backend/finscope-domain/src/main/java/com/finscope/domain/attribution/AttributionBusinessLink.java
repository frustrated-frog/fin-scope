package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 归因增量研究的独立快照，不覆盖原报告内容。 */
@Data
public class AttributionBusinessLink {
    private String business;
    private String position;
    private String financialAnchor;
    private String catalyst;
    private String transmission;
    private String sensitivity;
    private com.finscope.common.enums.attribution.NewsImpactDirection direction;
    private List<String> sourceIds = new ArrayList<>();
}
