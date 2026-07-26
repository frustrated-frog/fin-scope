package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchDecisionContext {
    private Long researchRunId;
    private int nextIteration;
    private int remainingActions;
    private String prompt;
    private ResearchMission mission;
    private ResearchAgentState state;
    private ResearchMissionGap latestGap;
    private List<String> attemptedFingerprints = Collections.emptyList();
    private String finishRejectionReason;

    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public int getNextIteration() { return nextIteration; }
    public void setNextIteration(int nextIteration) { this.nextIteration = nextIteration; }
    public int getRemainingActions() { return remainingActions; }
    public void setRemainingActions(int remainingActions) { this.remainingActions = remainingActions; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public ResearchMission getMission() { return mission; }
    public void setMission(ResearchMission mission) { this.mission = mission; }
    public ResearchAgentState getState() { return state; }
    public void setState(ResearchAgentState state) { this.state = state; }
    public ResearchMissionGap getLatestGap() { return latestGap; }
    public void setLatestGap(ResearchMissionGap latestGap) { this.latestGap = latestGap; }
    public List<String> getAttemptedFingerprints() { return attemptedFingerprints; }
    public void setAttemptedFingerprints(List<String> attemptedFingerprints) {
        this.attemptedFingerprints = attemptedFingerprints == null || attemptedFingerprints.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(attemptedFingerprints));
    }
    public String getFinishRejectionReason() { return finishRejectionReason; }
    public void setFinishRejectionReason(String finishRejectionReason) { this.finishRejectionReason = finishRejectionReason; }
}
