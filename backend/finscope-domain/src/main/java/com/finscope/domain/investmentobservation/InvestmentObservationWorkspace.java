package com.finscope.domain.investmentobservation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InvestmentObservationWorkspace {
    private List<InvestmentObservation> focus = new ArrayList<InvestmentObservation>();
    private List<InvestmentObservation> tracking = new ArrayList<InvestmentObservation>();
    private List<InvestmentObservation> learning = new ArrayList<InvestmentObservation>();
    private List<InvestmentObservation> archived = new ArrayList<InvestmentObservation>();
    private List<InvestmentObservationTransition> transitions = new ArrayList<InvestmentObservationTransition>();
    private int activeCount;
    private int changedTodayCount;
    private int waitingValidationCount;
    private int archivedCount;
    private String warning;
    private LocalDateTime refreshedAt;

    public List<InvestmentObservation> getFocus() { return focus; }
    public void setFocus(List<InvestmentObservation> focus) { this.focus = focus; }
    public List<InvestmentObservation> getTracking() { return tracking; }
    public void setTracking(List<InvestmentObservation> tracking) { this.tracking = tracking; }
    public List<InvestmentObservation> getLearning() { return learning; }
    public void setLearning(List<InvestmentObservation> learning) { this.learning = learning; }
    public List<InvestmentObservation> getArchived() { return archived; }
    public void setArchived(List<InvestmentObservation> archived) { this.archived = archived; }
    public List<InvestmentObservationTransition> getTransitions() { return transitions; }
    public void setTransitions(List<InvestmentObservationTransition> transitions) { this.transitions = transitions; }
    public int getActiveCount() { return activeCount; }
    public void setActiveCount(int activeCount) { this.activeCount = activeCount; }
    public int getChangedTodayCount() { return changedTodayCount; }
    public void setChangedTodayCount(int changedTodayCount) { this.changedTodayCount = changedTodayCount; }
    public int getWaitingValidationCount() { return waitingValidationCount; }
    public void setWaitingValidationCount(int waitingValidationCount) { this.waitingValidationCount = waitingValidationCount; }
    public int getArchivedCount() { return archivedCount; }
    public void setArchivedCount(int archivedCount) { this.archivedCount = archivedCount; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(LocalDateTime refreshedAt) { this.refreshedAt = refreshedAt; }
}
