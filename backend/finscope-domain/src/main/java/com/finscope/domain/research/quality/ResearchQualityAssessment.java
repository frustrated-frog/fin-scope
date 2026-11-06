package com.finscope.domain.research.quality;

import com.finscope.common.enums.research.ResearchFactKind;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchQualityAssessment {
    private ResearchTaskContract contract;
    private String fingerprint;
    private String extractionMode;
    private List<StructuredResearchFact> facts = new ArrayList<>();
    private List<ResearchEvidenceReview> evidence = new ArrayList<>();
    private List<ResearchFactKind> coveredFacts = new ArrayList<>();
    private List<String> missingConditions = new ArrayList<>();
    private List<String> conflicts = new ArrayList<>();
    private int independentSourceCount;
    private double taskCoverage;
    private String recoveryQuery;
    private String recoveryIntent;
    private List<String> feedback = new ArrayList<>();

    public boolean complete() {
        return missingConditions.isEmpty() && conflicts.isEmpty();
    }
}
