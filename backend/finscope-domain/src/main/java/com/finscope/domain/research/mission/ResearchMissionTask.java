package com.finscope.domain.research.mission;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class ResearchMissionTask {
    private Long id;
    private Long researchRunId;
    private String taskKey;
    private String title;
    private String question;
    private String taskType;
    private String toolCode;
    private String intent;
    private String status;
    private List<String> dependencies = Collections.emptyList();
    private String parallelGroup;
    private String queryText;
    private String rationale;
    private String expectedEvidence;
    private String outputSummary;
    private int evidenceDelta;
    private int sourceDelta;
    private String skipReason;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
