package com.finscope.service.research.method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ResearchMethodDefinition {
    private final String code;
    private final String name;
    private final String description;
    private final List<String> requiredQuestions;
    private final List<String> requiredEvidence;
    private final List<String> requiredCalculations;
    private final List<String> counterChecks;
    private final List<String> completionCriteria;
    private final List<String> requiredIntents;

    public ResearchMethodDefinition(String code, String name, String description,
                                    List<String> requiredQuestions, List<String> requiredEvidence,
                                    List<String> requiredCalculations, List<String> counterChecks,
                                    List<String> completionCriteria, List<String> requiredIntents) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.requiredQuestions = immutable(requiredQuestions);
        this.requiredEvidence = immutable(requiredEvidence);
        this.requiredCalculations = immutable(requiredCalculations);
        this.counterChecks = immutable(counterChecks);
        this.completionCriteria = immutable(completionCriteria);
        this.requiredIntents = immutable(requiredIntents);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getRequiredQuestions() { return requiredQuestions; }
    public List<String> getRequiredEvidence() { return requiredEvidence; }
    public List<String> getRequiredCalculations() { return requiredCalculations; }
    public List<String> getCounterChecks() { return counterChecks; }
    public List<String> getCompletionCriteria() { return completionCriteria; }
    public List<String> getRequiredIntents() { return requiredIntents; }

    private static List<String> immutable(List<String> values) {
        return values == null || values.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
