package com.finscope.domain.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRunPlan {
    /**
     * 研究运行对象。
     */
    private ResearchRun run;
    /**
     * 计划来源列表。
     */
    private List<SourceProfile> plannedSources = Collections.emptyList();
    /**
     * 计划步骤列表。
     */
    private List<ResearchRunPlanStep> planSteps = Collections.emptyList();

    public ResearchRun getRun() {
        return run;
    }

    public void setRun(ResearchRun run) {
        this.run = run;
    }

    public List<SourceProfile> getPlannedSources() {
        return plannedSources;
    }

    public void setPlannedSources(List<SourceProfile> plannedSources) {
        this.plannedSources = plannedSources == null ? Collections.<SourceProfile>emptyList()
                : Collections.unmodifiableList(new ArrayList<SourceProfile>(plannedSources));
    }

    public List<ResearchRunPlanStep> getPlanSteps() {
        return planSteps;
    }

    public void setPlanSteps(List<ResearchRunPlanStep> planSteps) {
        this.planSteps = planSteps == null ? Collections.<ResearchRunPlanStep>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchRunPlanStep>(planSteps));
    }
}
