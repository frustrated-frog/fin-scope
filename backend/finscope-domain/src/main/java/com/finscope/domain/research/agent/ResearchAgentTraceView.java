package com.finscope.domain.research.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchAgentTraceView {
    private ResearchAgentState state;
    private List<ResearchAgentDecision> decisions = Collections.emptyList();
    private List<ResearchToolObservation> observations = Collections.emptyList();
    private ResearchAgentTrajectoryMetrics trajectoryMetrics;

    public ResearchAgentState getState() { return state; }
    public void setState(ResearchAgentState state) { this.state = state; }
    public List<ResearchAgentDecision> getDecisions() { return decisions; }
    public void setDecisions(List<ResearchAgentDecision> decisions) {
        this.decisions = decisions == null || decisions.isEmpty()
                ? Collections.<ResearchAgentDecision>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchAgentDecision>(decisions));
    }
    public List<ResearchToolObservation> getObservations() { return observations; }
    public void setObservations(List<ResearchToolObservation> observations) {
        this.observations = observations == null || observations.isEmpty()
                ? Collections.<ResearchToolObservation>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchToolObservation>(observations));
    }
    public ResearchAgentTrajectoryMetrics getTrajectoryMetrics() { return trajectoryMetrics; }
    public void setTrajectoryMetrics(ResearchAgentTrajectoryMetrics trajectoryMetrics) {
        this.trajectoryMetrics = trajectoryMetrics;
    }
}
