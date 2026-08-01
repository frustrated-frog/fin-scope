package com.finscope.domain.research.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMethodBlueprint {
    private String researchType;
    private List<String> methodCodes = Collections.emptyList();
    private List<String> requiredEvidence = Collections.emptyList();
    private List<String> requiredCalculations = Collections.emptyList();
    private List<String> counterChecks = Collections.emptyList();
    private List<String> completionCriteria = Collections.emptyList();

    public String getResearchType() { return researchType; }
    public void setResearchType(String researchType) { this.researchType = researchType; }
    public List<String> getMethodCodes() { return methodCodes; }
    public void setMethodCodes(List<String> values) { this.methodCodes = immutable(values); }
    public List<String> getRequiredEvidence() { return requiredEvidence; }
    public void setRequiredEvidence(List<String> values) { this.requiredEvidence = immutable(values); }
    public List<String> getRequiredCalculations() { return requiredCalculations; }
    public void setRequiredCalculations(List<String> values) { this.requiredCalculations = immutable(values); }
    public List<String> getCounterChecks() { return counterChecks; }
    public void setCounterChecks(List<String> values) { this.counterChecks = immutable(values); }
    public List<String> getCompletionCriteria() { return completionCriteria; }
    public void setCompletionCriteria(List<String> values) { this.completionCriteria = immutable(values); }

    private static List<String> immutable(List<String> values) {
        return values == null || values.isEmpty() ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
