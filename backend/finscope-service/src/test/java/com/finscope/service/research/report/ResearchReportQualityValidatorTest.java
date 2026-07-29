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
        assertTrue(issues.contains("CORE_SECTION_UNGROUNDED:关键事实与 AI 解读"));
        assertTrue(issues.contains("CORE_SECTION_UNGROUNDED:不同解释与不确定性"));
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

    @Test
    void rejectsAReportWhoseCoreAnswerWasRemovedOrIsDominatedByAuditCaveats() {
        String report = report("[E1]", "[E1][E2]", "[E2]", true)
                .replace("测试公司当前结论。 [E1]", "**审计降级：** 核心断言已移除。[E1]")
                + "\n**审计降级：** 一\n**审计降级：** 二\n**审计降级：** 三"
                + "\n**审计降级：** 四\n**审计降级：** 五\n**审计降级：** 六\n";

        List<String> issues = validator.validate(report, thesis(), Arrays.asList(
                evidence("E1", "source-a", "SUPPORT"), evidence("E2", "source-b", "COUNTER")));

        assertTrue(issues.contains("CORE_CONCLUSION_OVER_REPAIRED"));
        assertTrue(issues.contains("EXCESSIVE_AUDIT_CAVEATS"));
    }

    @Test
    void rejectsMissingFactInterpretationPairsLegacyMetaSectionsAndTruncation() {
        String report = report("[E1]", "[E1][E2]", "[E2]", true)
                .replace("**AI 解读：**", "**普通说明：**")
                + "\n## 执行摘要\n系统使用了2条支持证据。\n\n（已截断）\n";

        List<String> issues = validator.validate(report, thesis(), Arrays.asList(
                evidence("E1", "source-a", "SUPPORT"), evidence("E2", "source-b", "COUNTER")));

        assertTrue(issues.contains("FACT_INTERPRETATION_MISMATCH"));
        assertTrue(issues.contains("LEGACY_META_SECTION_PRESENT"));
        assertTrue(issues.contains("TRUNCATION_MARKER_PRESENT"));
    }

    private String report(String conclusionRefs, String chainRefs, String counterRefs, boolean actionable) {
        String monitoring = actionable
                ? "若收入增速连续两个季度下滑，则下调判断；若现金流转负，命题失效。"
                : "后续继续关注收入和现金流表现。";
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 测试公司深度研究报告\n\n")
                .append("## 核心结论\n测试公司当前结论。 ").append(conclusionRefs).append("\n\n")
                .append("## 关键事实与 AI 解读\n### 事实 1\n\n**事实：** 可验证事实 ")
                .append(chainRefs).append("\n\n**AI 解读：** 该事实影响测试公司命题 ")
                .append(chainRefs).append("\n\n")
                .append("## 命题拆解与综合判断\n逐题判断 [E1]\n\n")
                .append("## 影响机制\n事件如何影响命题 [E1]\n\n")
                .append("## 不同解释与不确定性\n另一种解释 ").append(counterRefs).append("\n\n")
                .append("## 情景推演\n情景推演 [E1][E2]\n\n")
                .append("## 结论更新条件\n").append(monitoring).append("\n\n")
                .append("## 资料来源\n[E1][E2]\n\n");
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
