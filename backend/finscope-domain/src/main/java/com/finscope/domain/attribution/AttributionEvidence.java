package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归因证据：一条支撑归因结论的线索（来自全网搜索或本地新闻）。
 */
@Data
public class AttributionEvidence {
    /**
     * 主键 ID。
     */
    private Long id;

    /**
     * 归因报告 ID。
     */
    private Long reportId;
    /**
     * 来源。
     */
    private String origin;
    /**
     * 标题。
     */
    private String title;
    /**
     * 资源 URL。
     */
    private String url;
    /**
     * 摘要片段。
     */
    private String snippet;
    /**
     * 来源域名。
     */
    private String sourceDomain;
    /**
     * 来源层级。
     */
    private String sourceTier;
    /**
     * 相关度。
     */
    private Integer relevance;
    /**
     * 事件类型。
     */
    private String eventType;
    /**
     * 立场。
     */
    private String stance;
    /**
     * 直接程度。
     */
    private String directness;
    /**
     * 发布时间。
     */
    private String publishedAt;
    /**
     * 事件键。
     */
    private String eventKey;
    /**
     * 是否为历史背景信息。
     */
    private boolean historicalContext;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
