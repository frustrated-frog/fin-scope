package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归因证据：一条支撑归因结论的线索（来自全网搜索或本地新闻）。
 */
@Data
public class AttributionEvidence {
    private Long id;

    private Long reportId;
    /** 证据来源：WEB_SEARCH | LOCAL_NEWS | QUOTE */
    private String origin;
    private String title;
    private String url;
    /** 摘要/关键陈述 */
    private String snippet;
    private String sourceDomain;
    /** 来源可信度：T1 | T2 | T3 */
    private String sourceTier;
    /** 相关度 0~100 */
    private Integer relevance;
    /** COMPANY | INDUSTRY | MACRO | MARKET | COUNTER 等研究事件类别。 */
    private String eventType;
    /** SUPPORT | COUNTER | BACKGROUND。 */
    private String stance;
    /** DIRECT | INDIRECT | BACKGROUND。 */
    private String directness;
    /** 来源给出的发布时间原文，避免不同来源格式导致反序列化失败。 */
    private String publishedAt;
    /** 归一化后的事件聚合键。 */
    private String eventKey;
    /** 是否为历史报告带入的背景，不计入当日证据质量。 */
    private boolean historicalContext;

    private LocalDateTime createdAt;
}
