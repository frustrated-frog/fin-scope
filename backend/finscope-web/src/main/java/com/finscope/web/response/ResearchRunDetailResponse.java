package com.finscope.web.response;

import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRunPlanStep;
import com.finscope.domain.research.SourceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRunDetailResponse {
    private ResearchRun run;
    private List<SourceProfile> plannedSources = Collections.emptyList();
    private List<ResearchRunPlanStep> planSteps = Collections.emptyList();
    private List<AgentRun> agentRuns = Collections.emptyList();
    private boolean reportAvailable;
    private String reportStatus;
    private String reportGenerationMode;
    private boolean canRegenerateReport;

    public ResearchRunDetailResponse(ResearchRun run,
                                     List<SourceProfile> plannedSources,
                                     List<ResearchRunPlanStep> planSteps,
                                     List<AgentRun> agentRuns,
                                     ResearchReport report) {
        this.run = run;
        setPlannedSources(plannedSources);
        setPlanSteps(planSteps);
        this.agentRuns = agentRuns == null ? Collections.<AgentRun>emptyList() : agentRuns;
        this.reportAvailable = report != null;
        this.reportStatus = report == null ? null : report.getStatus();
        this.reportGenerationMode = report == null ? null : report.getGenerationMode();
        this.canRegenerateReport = run != null && run.getThesisId() != null && !"RUNNING".equals(run.getStatus());
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

    public List<ResearchRunPlanStep> getPlanSteps() {
        return planSteps;
    }

    public void setPlanSteps(List<ResearchRunPlanStep> planSteps) {
        this.planSteps = planSteps == null ? Collections.<ResearchRunPlanStep>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchRunPlanStep>(planSteps));
    }

    public List<AgentRun> getAgentRuns() {
        return agentRuns;
    }

    public void setAgentRuns(List<AgentRun> agentRuns) {
        this.agentRuns = agentRuns == null ? Collections.<AgentRun>emptyList() : agentRuns;
    }

    public boolean isReportAvailable() {
        return reportAvailable;
    }

    public String getReportStatus() {
        return reportStatus;
    }

    public String getReportGenerationMode() {
        return reportGenerationMode;
    }

    public boolean isCanRegenerateReport() {
        return canRegenerateReport;
    }
}
