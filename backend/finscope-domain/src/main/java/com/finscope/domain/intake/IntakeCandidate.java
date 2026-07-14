package com.finscope.domain.intake;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntakeCandidate {
    private Long id;
    private Long batchId;
    private Long sourceId;
    private String sourceName;
    private String sourceType;
    private String originalTitle;
    private String originalUrl;
    private String originalSummary;
    private String originalBody;
    private String contentType;
    private String extractionMethod;
    private int extractionQualityScore;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private String chineseTitle;
    private String decisionSummary;
    private String keyFactsJson;
    private String whyItMatters;
    private String noveltyJudgment;
    private String riskFlagsJson;
    private int agentScore;
    private String agentRecommendation;
    private String agentReason;
    private String agentModel;
    private String agentStatus;
    private String agentErrorMessage;
    private String agentReviewJson;
    private String humanStatus;
    private String humanNote;
    private Long promotedArticleId;
    private LocalDateTime promotedAt;
    private Long duplicateOfCandidateId;
    private Long duplicateOfArticleId;
    private String urlFingerprint;
    private String titleFingerprint;
    private String bodyFingerprint;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
