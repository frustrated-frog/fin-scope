package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 股票异动研判的结构化快照，用于模型输入、报告阅读和历史回看。 */
@Data
public class AttributionMarketContext {
    private String instrumentCode;
    private String reportDate;
    private String capturedAt;
    private String benchmarkName;
    private String benchmarkCode;
    private Double stockChangePct;
    private Double benchmarkChangePct;
    private Double relativeChangePct;
    private Double priorFiveSessionChangePct;
    private Double amountRatio;
    private boolean quoteVerified;
    private String source;
    private List<String> limitations = new ArrayList<>();
}
