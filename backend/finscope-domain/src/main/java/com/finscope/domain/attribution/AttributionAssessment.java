package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 股票异动研判的结构化快照，用于模型输入、报告阅读和历史回看。 */
@Data
public class AttributionAssessment {
    private int version = 1;
    /** 可选增量章节；旧报告为空。 */
    private AttributionEventContext eventContext;
    private com.finscope.common.enums.attribution.AssessmentStatus status;
    private AttributionMarketContext marketContext;
    private String researchFocus;
    private String focusReason;
    private List<String> missingInformation = new ArrayList<>();
    private String mainJudgment;
    private String pricingDebate;
    private List<String> explainedScope = new ArrayList<>();
    private List<String> unexplainedScope = new ArrayList<>();
    private List<AttributionHypothesis> hypotheses = new ArrayList<>();
    /** 短评由已验收判断的片段编排，不允许写作阶段引入新的事实。 */
    private List<String> commentary = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
