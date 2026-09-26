package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import com.finscope.common.enums.attribution.EventResearchFramework;
import com.finscope.common.enums.attribution.EventInformationChange;
import com.finscope.common.enums.attribution.NewsImpactDirection;

/** 事件脉络扩展快照，与已有归因字段独立保存。 */
@Data
public class AttributionEventDossier {
    private String title;
    private EventResearchFramework framework;
    private String relationshipBasis;
    private String currentStage;
    private EventInformationChange changeType;
    private String priorState;
    private String newInformation;
    private NewsImpactDirection impactDirection;
    private String impactAnalysis;
    private String changedJudgment;
    private String unchangedJudgment;
    private List<String> pendingConditions = new ArrayList<>();
    private List<AttributionEventMilestone> timeline = new ArrayList<>();
    private List<String> sourceIds = new ArrayList<>();
}
