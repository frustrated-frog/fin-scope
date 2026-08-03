package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchReportNarrativeAgentTest {
    @Test
    void mergesACompleteMarkerResponseIntoEveryNarrativeSlot() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        ResearchReportBlueprint blueprint = blueprint();
        when(llm.complete(anyString(), anyString(), eq(240000), eq(7000)))
                .thenReturn(allSections(blueprint));

        ResearchReportNarrative result = new ResearchReportNarrativeAgent(llm)
                .generate(thesis(), blueprint, dossier());

        assertTrue(result.isModelEnhanced());
        assertFalse(result.isRepaired());
        assertTrue(result.getExecutiveSummary().startsWith("模型执行摘要"));
        assertEquals(blueprint.getSubQuestions().size(), result.getSubQuestionAnalysis().size());
        assertEquals(blueprint.getArgumentChains().size(), result.getArgumentAnalysis().size());
        assertEquals(blueprint.getScenarios().size(), result.getScenarioAnalysis().size());
        verify(llm).complete(anyString(), anyString(), eq(240000), eq(7000));
    }

    @Test
    void keepsValidPartialSectionsAndRepairsOnlyMissingCriticalSections() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        ResearchReportBlueprint blueprint = blueprint();
        when(llm.complete(anyString(), anyString(), eq(240000), eq(7000)))
                .thenReturn(slot("EXECUTIVE_SUMMARY", "保留的模型摘要")
                        + slot("SUBQUESTION_1", "保留的第一子问题分析"))
                .thenReturn(slot("WHAT_HAPPENED", "修复后的事件脉络")
                        + slot("KNOWLEDGE_SYNTHESIS", "修复后的最终认识")
                        + slot("MONITORING_PLAN", "修复后的监测计划")
                        + slot("COUNTER_ANALYSIS", "修复后的反方分析"));

        ResearchReportNarrative result = new ResearchReportNarrativeAgent(llm)
                .generate(thesis(), blueprint, dossier());

        assertTrue(result.isModelEnhanced());
        assertTrue(result.isRepaired());
        assertEquals("保留的模型摘要", result.getExecutiveSummary());
        assertEquals("保留的第一子问题分析", result.getSubQuestionAnalysis().get(0));
        assertEquals("修复后的事件脉络", result.getWhatHappened());
        assertEquals(blueprint.getArgumentChains().size(), result.getArgumentAnalysis().size());
        verify(llm, times(2)).complete(anyString(), anyString(), eq(240000), eq(7000));
    }

    @Test
    void returnsCompleteBaselineWhenProviderKeepsReturningIncompatibleStructures() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        ResearchReportBlueprint blueprint = blueprint();
        when(llm.complete(anyString(), anyString(), eq(240000), eq(7000)))
                .thenReturn("{\"scenarioAnalysis\":{\"baseline\":\"对象而非数组\"}}")
                .thenThrow(new IllegalStateException("no final content with private reasoning"));

        ResearchReportNarrative result = new ResearchReportNarrativeAgent(llm)
                .generate(thesis(), blueprint, dossier());

        assertFalse(result.isModelEnhanced());
        assertTrue(result.getExecutiveSummary().contains("长鑫科技"));
        assertEquals(blueprint.getSubQuestions().size(), result.getSubQuestionAnalysis().size());
        assertEquals(blueprint.getArgumentChains().size(), result.getArgumentAnalysis().size());
        assertEquals(blueprint.getScenarios().size(), result.getScenarioAnalysis().size());
        assertFalse(result.getDiagnostics().isEmpty());
        assertFalse(result.getDiagnostics().get(0).contains("private reasoning"));
    }

    private ResearchReportBlueprint blueprint() {
        return new DeterministicReportBlueprintBuilder().build(thesis(), dossier(), true);
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("长鑫科技上市两日的市值和交易表现说明什么？");
        thesis.setSubjectName("长鑫科技");
        thesis.setSubjectType("COMPANY");
        return thesis;
    }

    private List<ResearchEvidenceDossier> dossier() {
        return Arrays.asList(
                evidence("E1", "SUPPORT", "首日成交额与市值显著上升，流通盘较小。"),
                evidence("E2", "COUNTER", "次日高换手说明定价分歧仍大。"),
                evidence("E3", "NEUTRAL", "总市值包含暂不可自由交易股份。"));
    }

    private ResearchEvidenceDossier evidence(String ref, String stance, String fact) {
        return new ResearchEvidenceDossier(ref, Long.valueOf(ref.substring(1)), null,
                "source-" + ref, "来源" + ref, "T1", "标题" + ref,
                LocalDateTime.of(2026, 7, 29, 10, 0), "https://example.com/" + ref,
                fact, stance, 90);
    }

    private String allSections(ResearchReportBlueprint blueprint) {
        StringBuilder out = new StringBuilder();
        out.append(slot("EXECUTIVE_SUMMARY", "模型执行摘要"));
        out.append(slot("WHAT_HAPPENED", "模型事件脉络"));
        for (int index = 1; index <= blueprint.getSubQuestions().size(); index++) {
            out.append(slot("SUBQUESTION_" + index, "模型子问题分析" + index));
        }
        for (int index = 1; index <= blueprint.getArgumentChains().size(); index++) {
            out.append(slot("ARGUMENT_" + index, "模型论证链分析" + index));
        }
        out.append(slot("COUNTER_ANALYSIS", "模型反方分析"));
        for (int index = 1; index <= blueprint.getScenarios().size(); index++) {
            out.append(slot("SCENARIO_" + index, "模型情景分析" + index));
        }
        out.append(slot("KNOWLEDGE_SYNTHESIS", "模型最终认识"));
        out.append(slot("MONITORING_PLAN", "模型监测计划"));
        return out.toString();
    }

    private String slot(String name, String value) {
        return "<<<" + name + ">>>\n" + value + "\n<<<END>>>\n";
    }
}
