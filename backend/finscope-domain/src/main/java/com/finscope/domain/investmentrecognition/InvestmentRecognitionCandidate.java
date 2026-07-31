package com.finscope.domain.investmentrecognition;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InvestmentRecognitionCandidate {
    private Long id;
    private String fingerprint;
    private String subjectType;
    private String subjectCode;
    private String subjectName;
    private String status;
    private String thesis;
    private String observedChange;
    private String mechanism;
    private List<String> supportingData = new ArrayList<String>();
    private List<String> counterData = new ArrayList<String>();
    private List<String> validationMetrics = new ArrayList<String>();
    private String invalidationConditions;
    private String horizon;
    private String confidence;
    private String evidenceCompleteness;
    private String triggerSummary;
    private String dataAsOf;
    private Long topicId;
    private long revision;
    private LocalDateTime generatedAt;
    private LocalDateTime updatedAt;
}
