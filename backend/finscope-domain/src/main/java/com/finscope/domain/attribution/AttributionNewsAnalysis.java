package com.finscope.domain.attribution;

import com.finscope.common.enums.attribution.NewsImpactDirection;
import com.finscope.common.enums.attribution.NewsInterpretationType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 单个驱动因素的专项解读，随原有 drivers JSON 持久化。 */
@Data
public class AttributionNewsAnalysis {
    private List<NewsInterpretationType> types = new ArrayList<>();
    private NewsImpactDirection direction;
    private String keyChange;
    private List<AttributionAnalysisPoint> businessImpacts = new ArrayList<>();
    private String shortTermImpact;
    private String mediumTermImpact;
    private String longTermImpact;
    private String chainReaction;
}
