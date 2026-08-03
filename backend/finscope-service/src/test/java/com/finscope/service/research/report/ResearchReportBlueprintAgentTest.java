package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchReportBlueprintAgentTest {
    @Test
    void enhancesOnlyServerOwnedTextSlotsAndPreservesReferencesAndShape() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000))).thenReturn(
                slot("DIRECT_ANSWER", "上市两日的高市值主要反映流通约束下的集中价格发现，不能单独证明长期价值。")
                        + slot("KEY_INSIGHT_1_MEANING", "总市值必须结合自由流通结构理解。")
                        + slot("SUBQUESTION_1_ANSWER", "交易事实确认关注度高，但价格发现尚未稳定。")
                        + slot("ARGUMENT_1_INFERENCE", "流通约束放大边际成交对价格的影响。")
                        + slot("COUNTER_RESPONSE", "需要以后续成交衰减和价格稳定性区分短期投机。"));
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        ResearchReportBlueprint result = agent.generate(thesis(), dossier());

        assertTrue(result.getDirectAnswer().startsWith("上市两日的高市值"));
        assertTrue(result.isModelEnhanced());
        assertEquals(3, result.getKeyInsights().size());
        assertEquals(4, result.getSubQuestions().size());
        assertTrue(result.getArgumentChains().size() >= 2);
        assertReferencesAreAllowed(result, new HashSet<String>(Arrays.asList("E1", "E2")));
        verify(llm).complete(anyString(), anyString(), eq(90000), eq(3000));
    }

    @Test
    void repairsJsonShapedProviderDriftWithASecondMarkerOnlyRequest() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000)))
                .thenReturn("{\"directAnswer\":\"旧版对象\",\"keyInsights\":\"字符串\"}")
                .thenReturn(slot("DIRECT_ANSWER", "修复后的直接回答")
                        + slot("KEY_INSIGHT_1_MEANING", "修复后的对象特定含义")
                        + slot("SUBQUESTION_1_ANSWER", "修复后的子问题回答"));
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        ResearchReportBlueprint result = agent.generate(thesis(), dossier());

        assertEquals("修复后的直接回答", result.getDirectAnswer());
        assertTrue(result.isModelEnhanced());
        assertTrue(result.isRepaired());
        verify(llm, times(2)).complete(anyString(), anyString(), eq(90000), eq(3000));
    }

    @Test
    void mergesRepairSlotsWithoutDiscardingValidFirstPassSlots() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000)))
                .thenReturn(slot("DIRECT_ANSWER", "首轮直接回答")
                        + slot("KEY_INSIGHT_1_MEANING", "首轮关键含义"))
                .thenReturn(slot("SUBQUESTION_1_ANSWER", "修复后的子问题回答"));

        ResearchReportBlueprint result = new ResearchReportBlueprintAgent(
                llm, new ResearchReportBlueprintValidator()).generate(thesis(), dossier());

        assertEquals("首轮直接回答", result.getDirectAnswer());
        assertEquals("首轮关键含义", result.getKeyInsights().get(0).getMeaning());
        assertEquals("修复后的子问题回答", result.getSubQuestions().get(0).getAnswer());
        assertTrue(result.isModelEnhanced());
    }

    @Test
    void returnsACompleteEvidenceBlueprintWhenBothModelCallsAreIncompatible() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000)))
                .thenReturn("{\"keyInsights\":{\"unexpected\":true}}")
                .thenThrow(new IllegalStateException("provider returned reasoning only"));
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        ResearchReportBlueprint result = agent.generate(thesis(), dossier());

        assertFalse(result.isModelEnhanced());
        assertTrue(result.getDirectAnswer().contains("长鑫科技"));
        assertEquals(3, result.getKeyInsights().size());
        assertEquals(3, result.getScenarios().size());
        assertFalse(result.getDiagnostics().isEmpty());
        assertFalse(result.getDiagnostics().get(0).contains("provider returned reasoning only"));
        assertReferencesAreAllowed(result, new HashSet<String>(Arrays.asList("E1", "E2")));
    }

    @Test
    void stripsModelSuppliedEvidenceIdentifiersFromEditableText() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000))).thenReturn(
                slot("DIRECT_ANSWER", "模型试图新增证据[E99]，也重复已有证据[E1]。")
                        + slot("KEY_INSIGHT_1_MEANING", "对象特定含义")
                        + slot("SUBQUESTION_1_ANSWER", "对象特定回答"));
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        ResearchReportBlueprint result = agent.generate(thesis(), dossier());

        assertFalse(result.getDirectAnswer().contains("[E99]"));
        assertFalse(result.getDirectAnswer().contains("[E1]"));
    }

    private void assertReferencesAreAllowed(ResearchReportBlueprint result, Set<String> allowed) {
        for (ResearchReportBlueprint.KeyInsight item : result.getKeyInsights()) {
            assertTrue(allowed.containsAll(item.getEvidenceRefs()));
        }
        for (ResearchReportBlueprint.ArgumentChain item : result.getArgumentChains()) {
            assertTrue(allowed.containsAll(item.getEvidenceRefs()));
        }
        assertTrue(allowed.containsAll(result.getStrongestCounterargument().getEvidenceRefs()));
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("长鑫科技上市两日的市值和交易表现说明什么？");
        thesis.setSubjectName("长鑫科技");
        thesis.setSubjectType("COMPANY");
        return thesis;
    }

    private List<ResearchEvidenceDossier> dossier() {
        Article first = article(1L, "交易所", "首日成交额创纪录", "流通盘较小，成交集中");
        Article second = article(2L, "财经媒体", "次日换手仍然较高", "价格波动扩大");
        return new ResearchEvidenceDossierBuilder().build(Arrays.asList(
                new ResearchEvidenceCard(first, null, "SUPPORT", 92, "首日成交额与市值显著上升"),
                new ResearchEvidenceCard(second, null, "COUNTER", 88, "高换手说明定价分歧仍大")));
    }

    private Article article(Long id, String source, String title, String summary) {
        Article article = new Article();
        article.setId(id);
        article.setSourceName(source);
        article.setTitle(title);
        article.setSummary(summary);
        article.setBody(summary + "，正文包含市值口径与交易结构解释。");
        article.setPublishedAt(LocalDateTime.of(2026, 7, 29, 10, 0));
        article.setUrl("https://example.com/" + id);
        return article;
    }

    private String slot(String name, String value) {
        return "<<<" + name + ">>>\n" + value + "\n<<<END>>>\n";
    }
}
