package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchReportGeneratorTest {
    private final ResearchReportGenerator generator = new ResearchReportGenerator();

    @Test
    void generatesAPlannedBoundedChineseReport() {
        ResearchThesis thesis = thesis();
        ResearchEvidenceSelector selector = new ResearchEvidenceSelector();
        List<ResearchEvidenceCard> evidence = selector.select(thesis, Arrays.asList(
                article(1L, "产业媒体", "半导体设备订单继续增长，晶圆厂上调资本开支", "设备需求维持景气"),
                article(2L, "公司公告", "晶圆厂宣布扩大成熟制程资本开支", "新增设备采购预算"),
                article(3L, "行业协会", "全球芯片销售额同比增长", "半导体周期延续复苏"),
                article(4L, "研究机构", "存储价格回升带动设备需求", "库存周期改善"),
                article(5L, "海外媒体", "Semiconductor equipment demand growth continues", "Wafer fab spending expands"),
                article(6L, "风险跟踪", "半导体设备订单下滑，部分晶圆厂削减资本开支", "周期存在分化风险")
        ), Collections.emptyList());

        GeneratedResearchReport report = generator.generate(thesis, evidence, EvidenceSufficiency.assess(evidence));

        assertTrue(report.getMarkdown().contains("## 核心结论"));
        assertTrue(report.getMarkdown().contains("# 半导体设备深度研究报告"));
        assertTrue(report.getMarkdown().contains("## 关键认识"));
        assertTrue(report.getMarkdown().contains("## 执行摘要"));
        assertTrue(report.getMarkdown().contains("## 研究范围与口径"));
        assertTrue(report.getMarkdown().contains("## 关键事实与数字"));
        assertTrue(report.getMarkdown().contains("| 证据 | 立场 | 时间 | 可验证事实 | 来源层级 | 相关性 |"));
        assertTrue(report.getMarkdown().contains("## 发生了什么"));
        assertTrue(report.getMarkdown().contains("## 命题拆解与逐题判断"));
        assertTrue(report.getMarkdown().contains("## 核心证据链"));
        assertTrue(report.getMarkdown().contains("## 反方解释与争议"));
        assertTrue(report.getMarkdown().contains("## 机制与情景推演"));
        assertTrue(report.getMarkdown().contains("## 最终认识与未知项"));
        assertTrue(report.getMarkdown().contains("## 跟踪清单与失效条件"));
        assertTrue(report.getMarkdown().contains("## 证据附录"));
        assertTrue(!report.getMarkdown().contains("支持与反向证据比"));
        assertTrue(report.getMarkdown().contains("相关性："));
        assertTrue(!report.getMarkdown().contains("（已截断）"));
        assertTrue(!report.getMarkdown().contains("…"));
        assertTrue(report.getMarkdown().contains("[E1](#evidence-e1)"));
        assertTrue(report.getMarkdown().contains("### E1 ·"));
        assertTrue(report.getMarkdown().contains("<a id=\"evidence-e1\"></a>"));
        assertTrue(report.getMarkdown().length() >= 6000);
        assertTrue(report.getMarkdown().length() <= ResearchReportPolicy.MAX_REPORT_CHARACTERS);
        assertTrue(report.getExecutiveSummary().length() > 100);
        assertTrue(report.getExecutiveSummary().length() <= ResearchReportPolicy.MAX_EXECUTIVE_SUMMARY_CHARACTERS);
        assertFalse(report.getConclusion().contains("无法得出结论"));
    }

    @Test
    void stillProducesAQualifiedConclusionWhenEvidenceIsThin() {
        ResearchThesis thesis = thesis();
        List<ResearchEvidenceCard> evidence = new ResearchEvidenceSelector().select(thesis,
                Collections.singletonList(article(1L, "产业媒体", "半导体设备订单增长", "需求景气")),
                Collections.emptyList());

        GeneratedResearchReport report = generator.generate(thesis, evidence, EvidenceSufficiency.assess(evidence));

        assertTrue(report.getConclusion().startsWith("阶段性结论"));
        assertTrue(report.getMarkdown().contains("当前资料尚未覆盖全部必要方向"));
        assertTrue(report.getMarkdown().length() < 12000);
    }

    @Test
    void doesNotInjectSemiconductorSpecificSignalsIntoACompanyReport() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("苹果公司盈利预期是否仍在上修");
        thesis.setSubjectName("苹果公司");
        thesis.setSubjectType("COMPANY");
        List<ResearchEvidenceCard> evidence = new ResearchEvidenceSelector().select(thesis,
                Collections.singletonList(article(1L, "公司公告", "苹果公司上调盈利指引", "收入和利润率预期改善")),
                Collections.emptyList());

        GeneratedResearchReport report = generator.generate(thesis, evidence, EvidenceSufficiency.assess(evidence));

        assertTrue(report.getConclusion().contains("苹果公司盈利预期是否仍在上修"));
        assertTrue(report.getMarkdown().contains("收入、利润率、现金流与管理层指引"));
        assertFalse(report.getMarkdown().contains("晶圆厂"));
        assertFalse(report.getMarkdown().contains("设备采购"));
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("科技板块冲高后近期大跌回落，周期是否还能持续");
        thesis.setSubjectName("半导体设备");
        thesis.setSubjectType("INDUSTRY");
        return thesis;
    }

    private Article article(Long id, String source, String title, String summary) {
        Article article = new Article();
        article.setId(id);
        article.setSourceName(source);
        article.setTitle(title);
        article.setSummary(summary);
        article.setUrl("https://example.com/" + id);
        return article;
    }

    private int occurrences(String value, String expected) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(expected, offset)) >= 0; offset += expected.length()) count++;
        return count;
    }
}
