package com.finscope.service.research.mission;

public class ResearchPlanningResult {
    private final ResearchMissionDraft draft;
    private final String planningMode;
    private final String fallbackReason;
    private final String rejectionDetail;

    public ResearchPlanningResult(ResearchMissionDraft draft,
                                  String planningMode,
                                  String fallbackReason,
                                  String rejectionDetail) {
        this.draft = draft;
        this.planningMode = planningMode;
        this.fallbackReason = fallbackReason;
        this.rejectionDetail = rejectionDetail;
    }

    public ResearchMissionDraft getDraft() {
        return draft;
    }

    public String getPlanningMode() {
        return planningMode;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public String getRejectionDetail() {
        return rejectionDetail;
    }
}
