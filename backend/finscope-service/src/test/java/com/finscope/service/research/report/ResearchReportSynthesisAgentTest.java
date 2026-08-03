package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchReportSynthesisAgentTest {
    @Test
    void persistsStructuredModelModeWhenTheWholePipelinePasses() throws Exception {
        ResearchEvidenceDossierBuilder dossierBuilder = mock(ResearchEvidenceDossierBuilder.class);
        ResearchReportBlueprintAgent blueprintAgent = mock(ResearchReportBlueprintAgent.class);
        ResearchReportNarrativeAgent narrativeAgent = mock(ResearchReportNarrativeAgent.class);
        StructuredResearchReportAssembler assembler = mock(StructuredResearchReportAssembler.class);
        ResearchReportQualityValidator qualityValidator = mock(ResearchReportQualityValidator.class);
        ResearchReportBlueprint blueprint = new ResearchReportBlueprint();
        blueprint.setDirectAnswer("对象特定结论");
        blueprint.setDirection("MIXED");
        blueprint.setConfidence("MEDIUM");
        blueprint.setModelEnhanced(true);
        ResearchReportNarrative narrative = new ResearchReportNarrative();
        narrative.setExecutiveSummary("对象特定执行摘要");
        narrative.setModelEnhanced(true);
        when(dossierBuilder.build(anyList())).thenReturn(Collections.<ResearchEvidenceDossier>emptyList());
        when(blueprintAgent.generate(any(), anyList())).thenReturn(blueprint);
        when(narrativeAgent.generate(any(), any(), anyList())).thenReturn(narrative);
        when(assembler.assemble(any(), any(), any(), anyList())).thenReturn("结构化深度正文");
        when(qualityValidator.validate(anyString(), any(), anyList())).thenReturn(Collections.<String>emptyList());
        ResearchReportSynthesisAgent agent = new ResearchReportSynthesisAgent(
                dossierBuilder, blueprintAgent, narrativeAgent, assembler, qualityValidator);

        GeneratedResearchReport result = agent.refine(new ResearchThesis(), sufficientEvidence(), fallback());

        assertEquals("MODEL_STRUCTURED", result.getGenerationMode());
        assertEquals("对象特定结论", result.getConclusion());
        assertEquals("对象特定执行摘要", result.getExecutiveSummary());
        assertEquals("", result.getWarning());
    }

    @Test
    void continuesNarrativeEnhancementWhenBlueprintModelStageFails() throws Exception {
        ResearchEvidenceDossierBuilder dossierBuilder = mock(ResearchEvidenceDossierBuilder.class);
        ResearchReportBlueprintAgent blueprintAgent = mock(ResearchReportBlueprintAgent.class);
        ResearchReportNarrativeAgent narrativeAgent = mock(ResearchReportNarrativeAgent.class);
        StructuredResearchReportAssembler assembler = mock(StructuredResearchReportAssembler.class);
        ResearchReportQualityValidator qualityValidator = mock(ResearchReportQualityValidator.class);
        ResearchReportNarrative narrative = new ResearchReportNarrative();
        narrative.setExecutiveSummary("模型仍然完成了对象特定正文");
        narrative.setModelEnhanced(true);
        when(dossierBuilder.build(anyList())).thenReturn(Collections.<ResearchEvidenceDossier>emptyList());
        when(blueprintAgent.generate(any(), anyList()))
                .thenThrow(new ResearchReportGenerationException("BLUEPRINT_MODEL_CALL_FAILED:SocketTimeoutException"));
        when(narrativeAgent.generate(any(), any(), anyList())).thenReturn(narrative);
        when(assembler.assemble(any(), any(), any(), anyList())).thenReturn("结构化深度正文");
        when(qualityValidator.validate(anyString(), any(), anyList())).thenReturn(Collections.<String>emptyList());
        ResearchReportSynthesisAgent agent = new ResearchReportSynthesisAgent(dossierBuilder, blueprintAgent,
                narrativeAgent, assembler, qualityValidator);

        GeneratedResearchReport result = agent.refine(new ResearchThesis(), sufficientEvidence(), fallback());

        assertEquals("MODEL_REPAIRED", result.getGenerationMode());
        assertTrue(result.getWarning().contains("BLUEPRINT_MODEL_CALL_FAILED:SocketTimeoutException"));
        verify(narrativeAgent).generate(any(), any(), anyList());
    }

    @Test
    void marksPartiallyCompletedNarrativeAsModelRepairedInsteadOfFallingBack() throws Exception {
        ResearchEvidenceDossierBuilder dossierBuilder = mock(ResearchEvidenceDossierBuilder.class);
        ResearchReportBlueprintAgent blueprintAgent = mock(ResearchReportBlueprintAgent.class);
        ResearchReportNarrativeAgent narrativeAgent = mock(ResearchReportNarrativeAgent.class);
        StructuredResearchReportAssembler assembler = mock(StructuredResearchReportAssembler.class);
        ResearchReportQualityValidator qualityValidator = mock(ResearchReportQualityValidator.class);
        ResearchReportBlueprint blueprint = new ResearchReportBlueprint();
        blueprint.setDirectAnswer("对象特定结论");
        blueprint.setDirection("MIXED");
        blueprint.setConfidence("MEDIUM");
        blueprint.setModelEnhanced(true);
        ResearchReportNarrative narrative = new ResearchReportNarrative();
        narrative.setExecutiveSummary("模型正文与证据基线共同完成");
        narrative.setModelEnhanced(true);
        narrative.setRepaired(true);
        narrative.setModelSectionCount(8);
        narrative.setExpectedModelSectionCount(14);
        narrative.getDiagnostics().add("NARRATIVE_MODEL_FORMAT_INCOMPLETE");
        when(dossierBuilder.build(anyList())).thenReturn(Collections.<ResearchEvidenceDossier>emptyList());
        when(blueprintAgent.generate(any(), anyList())).thenReturn(blueprint);
        when(narrativeAgent.generate(any(), any(), anyList())).thenReturn(narrative);
        when(assembler.assemble(any(), any(), any(), anyList())).thenReturn("结构化深度正文");
        when(qualityValidator.validate(anyString(), any(), anyList())).thenReturn(Collections.<String>emptyList());
        ResearchReportSynthesisAgent agent = new ResearchReportSynthesisAgent(
                dossierBuilder, blueprintAgent, narrativeAgent, assembler, qualityValidator);

        GeneratedResearchReport result = agent.refine(new ResearchThesis(), sufficientEvidence(), fallback());

        assertEquals("MODEL_REPAIRED", result.getGenerationMode());
        assertTrue(result.getWarning().contains("NARRATIVE_MODEL_FORMAT_INCOMPLETE"));
    }

    @Test
    void repairsUnsupportedClaimOnceBeforeAcceptingModelReport() throws Exception {
        ResearchEvidenceDossierBuilder dossierBuilder = mock(ResearchEvidenceDossierBuilder.class);
        ResearchReportBlueprintAgent blueprintAgent = mock(ResearchReportBlueprintAgent.class);
        ResearchReportNarrativeAgent narrativeAgent = mock(ResearchReportNarrativeAgent.class);
        StructuredResearchReportAssembler assembler = mock(StructuredResearchReportAssembler.class);
        ResearchReportQualityValidator qualityValidator = mock(ResearchReportQualityValidator.class);
        ResearchReportRepairAgent repairAgent = mock(ResearchReportRepairAgent.class);
        ResearchEvidenceDossier dossier = new ResearchEvidenceDossier("E1", null, null, "example.com",
                "示例来源", "T2", "业绩公告", null, "https://example.com/e1",
                "公司披露2025年收入增长18%。", "SUPPORT", 90);
        ResearchReportBlueprint blueprint = new ResearchReportBlueprint();
        blueprint.setDirectAnswer("对象特定结论");
        blueprint.setDirection("SUPPORT");
        blueprint.setConfidence("MEDIUM");
        blueprint.setModelEnhanced(true);
        ResearchReportNarrative narrative = new ResearchReportNarrative();
        narrative.setExecutiveSummary("对象特定执行摘要");
        narrative.setModelEnhanced(true);
        String unsupported = "## 核心结论\n\n公司披露2025年收入增长25%。[E1]";
        String repaired = "## 核心结论\n\n公司披露2025年收入增长18%。[E1]";
        when(dossierBuilder.build(anyList())).thenReturn(Collections.singletonList(dossier));
        when(blueprintAgent.generate(any(), anyList())).thenReturn(blueprint);
        when(narrativeAgent.generate(any(), any(), anyList())).thenReturn(narrative);
        when(assembler.assemble(any(), any(), any(), anyList())).thenReturn(unsupported);
        when(repairAgent.repair(anyString(), any(), anyList())).thenReturn(repaired);
        when(qualityValidator.validate(anyString(), any(), anyList())).thenReturn(Collections.emptyList());
        ResearchReportSynthesisAgent agent = new ResearchReportSynthesisAgent(dossierBuilder, blueprintAgent,
                narrativeAgent, assembler, qualityValidator,
                new ResearchClaimAuditor(new ResearchClaimExtractor()), repairAgent);

        GeneratedResearchReport result = agent.refine(new ResearchThesis(), sufficientEvidence(), fallback());

        assertEquals("MODEL_CLAIM_REPAIRED", result.getGenerationMode());
        assertTrue(result.getMarkdown().contains("18%"));
        verify(repairAgent).repair(anyString(), any(), anyList());
    }

    private GeneratedResearchReport fallback() {
        return new GeneratedResearchReport("标题", "阶段性结论", "MIXED", "LOW", "摘要",
                "# 标题\n\n## 核心结论\n\n阶段性结论\n\n## 执行摘要\n\n摘要\n\n## 命题拆解\n\n- A\n\n"
                        + "## 关键证据\n\n- A\n\n## 反方证据与风险\n\n- A\n\n## 结论边界与后续验证\n\n- A\n\n## 来源\n\n- A",
                "DETERMINISTIC");
    }

    private List<ResearchEvidenceCard> sufficientEvidence() {
        List<ResearchEvidenceCard> cards = new ArrayList<ResearchEvidenceCard>();
        for (int index = 0; index < 6; index++) {
            Article article = new Article();
            article.setId((long) index + 1);
            article.setSourceName(index % 2 == 0 ? "来源甲" : "来源乙");
            article.setTitle("半导体设备经营证据 " + index);
            article.setUrl("https://example.com/" + index);
            cards.add(new ResearchEvidenceCard(article, null, index == 5 ? "COUNTER" : "SUPPORT", 80, "证据 " + index));
        }
        return cards;
    }
}
