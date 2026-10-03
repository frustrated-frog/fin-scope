package com.finscope.domain.attribution;

import lombok.Data;

/** 按新闻类型选择的专项分析维度，如订单兑现周期、利润质量。 */
@Data
public class AttributionAnalysisPoint {
    private String label;
    private String analysis;
}
