package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchReportQualityValidatorTest {
    private final ResearchReportQualityValidator validator = new ResearchReportQualityValidator();

    @Test
    void rejectsLowEvidenceCoverageAndSingleSourceDossier() {
        List<ResearchEvidenceDossier> dossier = Arrays.asList(
                evidence("E1", "source-a", "SUPPORT"),
                evidence("E2", "source-a", "SUPPORT"),
                evidence("E3", "source-a", "COUNTER"),
                evidence("E4", "source-a", "UNKNOWN"),
                evidence("E5", "source-a", "UNKNOWN"));

        List<String> issues = validator.validate(report("[E1]", "[E1]", "[E1]", true), thesis(), dossier);

        assertTrue(issues.contains("INSUFFICIENT_CITATION_COVERAGE"));
        assertTrue(issues.contains("SOURCE_DIVERSITY_INSUFFICIENT"));
    }

    @Test
    void requiresGroundingInDecisionCriticalSections() {
        List<String> issues = validator.validate(report("没有引用", "没有引用", "没有引用", true),
                thesis(), Arrays.asList(evidence("E1", "source-a", "SUPPORT"),
                        evidence("E2", "source-b", "COUNTER")));

        assertTrue(issues.contains("CORE_SECTION_UNGROUNDED:核心结论"));
        assertTrue(issues.contains("CORE_SECTION_UNGROUNDED:核心证据链"));
        assertTrue(issues.contains("CORE_SECTION_UNGROUNDED:反方解释与争议"));
    }

    @Test
    void requiresAvailableCounterEvidenceToAppearInCounterSection() {
        List<String> issues = validator.validate(report("[E1]", "[E1][E2]", "[E1]", true),
                thesis(), Arrays.asList(evidence("E1", "source-a", "SUPPORT"),
                        evidence("E2", "source-b", "COUNTER")));

        assertTrue(issues.contains("COUNTER_EVIDENCE_MISSING"));
    }

    @Test
    void requiresActionableDowngradeOrInvalidationCondition() {
        List<String> issues = validator.validate(report("[E1]", "[E1][E2]", "[E2]", false),
                thesis(), Arrays.asList(evidence("E1", "source-a", "SUPPORT"),
                        evidence("E2", "source-b", "COUNTER")));

        assertTrue(issues.contains("MONITORING_CONDITION_MISSING"));
    }

    @Test
    void acceptsGroundedDiverseAndActionableResearchReport() {
        List<String> issues = validator.validate(report("[E1]", "[E1][E2]", "[E2]", true),
                thesis(), Arrays.asList(evidence("E1", "source-a", "SUPPORT"),
                        evidence("E2", "source-b", "COUNTER")));

        assertFalse(issues.contains("INSUFFICIENT_CITATION_COVERAGE"));
        assertFalse(issues.contains("SOURCE_DIVERSITY_INSUFFICIENT"));
        assertFalse(issues.stream().anyMatch(item -> item.startsWith("CORE_SECTION_UNGROUNDED:")));
        assertFalse(issues.contains("COUNTER_EVIDENCE_MISSING"));
        assertFalse(issues.contains("MONITORING_CONDITION_MISSING"));
    }

    private String report(String conclusionRefs, String chainRefs, String counterRefs, boolean actionable) {
        String monitoring = actionable
                ? "若收入增速连续两个季度下滑，则下调判断；若现金流转负，命题失效。"
                : "后续继续关注收入和现金流表现。";
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 测试公司深度研究报告\n\n")
                .append("## 核心结论\n测试公司当前结论。 ").append(conclusionRefs).append("\n\n")
                .append("## 关键认识\n关键认识 [E1]\n\n")
                .append("## 执行摘要\n执行摘要 [E1]\n\n")
                .append("## 研究范围与口径\n研究范围。\n\n")
                .append("## 关键事实与数字\n事实 [E1][E2]\n\n")
                .append("## 发生了什么\n事件经过 [E1]\n\n")
                .append("## 命题拆解与逐题判断\n逐题判断 [E1]\n\n")
                .append("## 核心证据链\n证据链 ").append(chainRefs).append("\n\n")
                .append("## 反方解释与争议\n反方解释 ").append(counterRefs).append("\n\n")
                .append("## 机制与情景推演\n情景推演 [E1][E2]\n\n")
                .append("## 最终认识与未知项\n未知项。\n\n")
                .append("## 跟踪清单与失效条件\n").append(monitoring).append("\n\n")
                .append("## 证据附录\n[E1][E2]\n\n");
        while (markdown.length() < 6100) markdown.append("测试公司研究正文用于验证报告长度与结构。\n");
        return markdown.toString();
    }

    private ResearchEvidenceDossier evidence(String ref, String source, String stance) {
        return new ResearchEvidenceDossier(ref, Long.valueOf(ref.substring(1)), null, source,
                source, "MEDIA", "证据标题", LocalDateTime.of(2026, 7, 29, 10, 0),
                "https://example.com/" + ref, "可验证事实", stance, 90);
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("测试公司盈利能力是否改善？");
        thesis.setSubjectName("测试公司");
        thesis.setSubjectType("COMPANY");
        return thesis;
    }
}
