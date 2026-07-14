package com.finscope.domain.intake;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntakeCandidate {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 批次 ID。
     */
    private Long batchId;
    /**
     * 信息源 ID。
     */
    private Long sourceId;
    /**
     * 信息源名称。
     */
    private String sourceName;
    /**
     * 信息源类型。
     */
    private String sourceType;
    /**
     * 原始标题。
     */
    private String originalTitle;
    /**
     * 原始 URL。
     */
    private String originalUrl;
    /**
     * 原始摘要。
     */
    private String originalSummary;
    /**
     * 原始正文。
     */
    private String originalBody;
    /**
     * 内容类型。
     */
    private String contentType;
    /**
     * 抽取方式。
     */
    private String extractionMethod;
    /**
     * 抽取质量评分。
     */
    private int extractionQualityScore;
    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;
    /**
     * 抓取时间。
     */
    private LocalDateTime fetchedAt;
    /**
     * 中文标题。
     */
    private String chineseTitle;
    /**
     * 审核决策摘要。
     */
    private String decisionSummary;
    /**
     * 关键事实 JSON。
     */
    private String keyFactsJson;
    /**
     * 重要性说明。
     */
    private String whyItMatters;
    /**
     * 新意判断。
     */
    private String noveltyJudgment;
    /**
     * 风险提示 JSON。
     */
    private String riskFlagsJson;
    /**
     * 智能体评分。
     */
    private int agentScore;
    /**
     * 智能体推荐结论。
     */
    private String agentRecommendation;
    /**
     * 智能体推荐原因。
     */
    private String agentReason;
    /**
     * 智能体模型名称。
     */
    private String agentModel;
    /**
     * 智能体处理状态。
     */
    private String agentStatus;
    /**
     * 智能体错误信息。
     */
    private String agentErrorMessage;
    /**
     * 智能体审核 JSON。
     */
    private String agentReviewJson;
    /**
     * 人工审核状态。
     */
    private String humanStatus;
    /**
     * 人工审核备注。
     */
    private String humanNote;
    /**
     * 提升后生成的文章 ID。
     */
    private Long promotedArticleId;
    /**
     * 提升时间。
     */
    private LocalDateTime promotedAt;
    /**
     * 重复候选 ID。
     */
    private Long duplicateOfCandidateId;
    /**
     * 重复文章 ID。
     */
    private Long duplicateOfArticleId;
    /**
     * URL 指纹。
     */
    private String urlFingerprint;
    /**
     * 标题指纹。
     */
    private String titleFingerprint;
    /**
     * 正文指纹。
     */
    private String bodyFingerprint;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
