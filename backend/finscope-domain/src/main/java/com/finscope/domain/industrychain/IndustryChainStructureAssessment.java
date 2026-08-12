package com.finscope.domain.industrychain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 已发布产业图谱的通用结构完整度。 */
@Data
public class IndustryChainStructureAssessment {
    private String status;
    private int score;
    private int semanticNodeCount;
    private int coveredStageCount;
    private int stageCount;
    private List<String> gaps = new ArrayList<String>();
}
