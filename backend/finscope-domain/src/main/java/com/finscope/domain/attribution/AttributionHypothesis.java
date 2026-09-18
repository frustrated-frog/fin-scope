package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 股票异动研判的结构化快照，用于模型输入、报告阅读和历史回看。 */
@Data
public class AttributionHypothesis {
    private String id;
    private String explanation;
    private com.finscope.common.enums.attribution.HypothesisDisposition disposition;
    private String selectionReason;
    private String pricingMechanism;
    private String explains;
    private String doesNotExplain;
    private List<String> evidenceUrls = new ArrayList<>();
    private List<String> assumptions = new ArrayList<>();
    private List<String> revisionConditions = new ArrayList<>();
}
