package com.finscope.service.research.method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ResearchMethodSelection {
    private final String researchType;
    private final List<String> methodCodes;
    private final List<String> requiredEvidence;
    private final List<String> requiredCalculations;
    private final List<String> counterChecks;
    private final List<String> completionCriteria;

    ResearchMethodSelection(String researchType, List<String> methodCodes, List<String> requiredEvidence,
                            List<String> requiredCalculations, List<String> counterChecks,
                            List<String> completionCriteria) {
        this.researchType = researchType;
        this.methodCodes = immutable(methodCodes);
        this.requiredEvidence = immutable(requiredEvidence);
        this.requiredCalculations = immutable(requiredCalculations);
        this.counterChecks = immutable(counterChecks);
        this.completionCriteria = immutable(completionCriteria);
    }

    public String getResearchType() { return researchType; }
    public List<String> getMethodCodes() { return methodCodes; }
    public List<String> getRequiredEvidence() { return requiredEvidence; }
    public List<String> getRequiredCalculations() { return requiredCalculations; }
    public List<String> getCounterChecks() { return counterChecks; }
    public List<String> getCompletionCriteria() { return completionCriteria; }

    private static List<String> immutable(List<String> values) {
        return values == null || values.isEmpty() ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
