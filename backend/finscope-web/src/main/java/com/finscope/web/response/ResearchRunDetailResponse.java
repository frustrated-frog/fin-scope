package com.finscope.web.response;

import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.SourceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRunDetailResponse {
    private ResearchRun run;
    private List<SourceProfile> plannedSources = Collections.emptyList();
    private List<AgentRun> agentRuns = Collections.emptyList();

    public ResearchRunDetailResponse(ResearchRun run, List<SourceProfile> plannedSources, List<AgentRun> agentRuns) {
        this.run = run;
        setPlannedSources(plannedSources);
        this.agentRuns = agentRuns == null ? Collections.<AgentRun>emptyList() : agentRuns;
    }

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

    public List<AgentRun> getAgentRuns() {
        return agentRuns;
    }

    public void setAgentRuns(List<AgentRun> agentRuns) {
        this.agentRuns = agentRuns == null ? Collections.<AgentRun>emptyList() : agentRuns;
    }
}
