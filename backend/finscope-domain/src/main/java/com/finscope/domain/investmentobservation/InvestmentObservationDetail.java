package com.finscope.domain.investmentobservation;

import java.util.ArrayList;
import java.util.List;

public class InvestmentObservationDetail {
    private InvestmentObservation observation;
    private List<InvestmentObservationTransition> transitions = new ArrayList<InvestmentObservationTransition>();

    public InvestmentObservation getObservation() { return observation; }
    public void setObservation(InvestmentObservation observation) { this.observation = observation; }
    public List<InvestmentObservationTransition> getTransitions() { return transitions; }
    public void setTransitions(List<InvestmentObservationTransition> transitions) { this.transitions = transitions; }
}
