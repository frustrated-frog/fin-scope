package com.finscope.domain.insight;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InsightCard {
    private Long id;
    private Long articleId;
    private String title;
    private String sourceName;
    private String sourceUrl;
    private LocalDateTime publishedAt;
    private String oneSentenceSummary;
    private String coreEvent;
    private String importance;
    private String impactTargets;
    private String noveltyType;
    private String noveltyReason;
    private String followUpQuestions;
    private String cardMarkdown;

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
    private List<InsightSection> analysisSections = new ArrayList<InsightSection>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;



    public void setAnalysisSections(List<InsightSection> analysisSections) {
        this.analysisSections = analysisSections == null
                ? new ArrayList<InsightSection>()
                : new ArrayList<InsightSection>(analysisSections);
    }

}
