package com.finscope.domain.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRunPlan {
    private ResearchRun run;
    private List<SourceProfile> plannedSources = Collections.emptyList();

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
}
