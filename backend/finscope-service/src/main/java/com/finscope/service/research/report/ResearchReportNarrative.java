package com.finscope.service.research.report;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchReportNarrative {
    private transient boolean repaired;
    private transient boolean modelEnhanced;
    private transient int modelSectionCount;
    private transient int expectedModelSectionCount;
    private transient List<String> diagnostics = new ArrayList<String>();
    private String executiveSummary;
    private String whatHappened;
    private List<String> subQuestionAnalysis = new ArrayList<String>();
    private List<String> argumentAnalysis = new ArrayList<String>();
    private String counterAnalysis;
    private List<String> scenarioAnalysis = new ArrayList<String>();
    private String knowledgeSynthesis;
    private String monitoringPlan;
}
