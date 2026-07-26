package com.finscope.domain.insight;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InsightCard {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 文章 ID。
     */
    private Long articleId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 信息源名称。
     */
    private String sourceName;
    /**
     * 信息源地址。
     */
    private String sourceUrl;
    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;
    /**
     * 一句话摘要。
     */
    private String oneSentenceSummary;
    /**
     * 核心事件。
     */
    private String coreEvent;
    /**
     * 重要性说明。
     */
    private String importance;
    /**
     * 影响对象。
     */
    private String impactTargets;
    /**
     * 新意类型。
     */
    private String noveltyType;
    /**
     * 新意判断原因。
     */
    private String noveltyReason;
    /**
     * 后续问题列表。
     */
    private String followUpQuestions;
    /**
     * 卡片 Markdown 内容。
     */
    private String cardMarkdown;
    /** 解读生成来源：LLM、FALLBACK 或 UNKNOWN。 */
    private String interpretationSource;

    // 新增字段：深度解读
    private String background;           // 背景是什么
    private String keyData;              // 关键数据
    private String timeline;             // 时间线
    private String relatedParties;       // 相关方
    private String riskFactors;          // 风险因素
    private String futureOutlook;        // 未来展望
    private String impactOnInvestment;   // 对投资的影响
    private String impactOnStartup;      // 对创业的影响
    private String professionalInsight;  // 专业解读
    private String facts;                // 事实
    private String reasoning;            // 推理
    private String opinions;             // 观点
    /**
     * 分析分段列表。
     */
    private List<InsightSection> analysisSections = new ArrayList<InsightSection>();

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;



    public void setAnalysisSections(List<InsightSection> analysisSections) {
        this.analysisSections = analysisSections == null
                ? new ArrayList<InsightSection>()
                : new ArrayList<InsightSection>(analysisSections);
    }

}
