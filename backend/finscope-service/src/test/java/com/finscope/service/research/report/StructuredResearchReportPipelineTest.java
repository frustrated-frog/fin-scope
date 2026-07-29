package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredResearchReportPipelineTest {
    @Test
    void generatesDeepNarrativeAndAssemblesTraceableReport() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(120000), eq(7000))).thenReturn(narrativeJson());
        ResearchReportNarrativeAgent agent = new ResearchReportNarrativeAgent(llm);
        ResearchThesis thesis = thesis();
        ResearchReportBlueprint blueprint = blueprint();
        List<ResearchEvidenceDossier> dossier = dossier();

        ResearchReportNarrative narrative = agent.generate(thesis, blueprint, dossier);
        narrative.setExecutiveSummary("模型摘要没有显式引用。");
        String markdown = new StructuredResearchReportAssembler().assemble(thesis, blueprint, narrative, dossier);

        assertEquals(3, narrative.getSubQuestionAnalysis().size());
        assertTrue(markdown.contains("## 关键事实与 AI 解读"));
        assertTrue(markdown.contains("**事实：** 对象特定事实 1"));
        assertTrue(markdown.contains("**AI 解读：**"));
        assertTrue(markdown.contains("## 命题拆解与综合判断"));
        assertTrue(markdown.contains("## 不同解释与不确定性"));
        assertTrue(markdown.contains("## 资料来源"));
        assertTrue(!markdown.contains("## 执行摘要"));
        assertTrue(!markdown.contains("支持与反向证据比"));
        assertTrue(markdown.contains("[E1](#evidence-e1)"));
        assertTrue(!markdown.contains("<a id=\"evidence-e1\"></a>"));
        assertTrue(!markdown.contains("\\n"));
        verify(llm).complete(anyString(), anyString(), eq(120000), eq(7000));
    }

    @Test
    void qualityValidatorRejectsUnknownEvidenceAndGenericShortReport() {
        List<String> issues = new ResearchReportQualityValidator().validate(
                "## 核心结论\n通用判断 [E99](#evidence-e99)\n## 来源\n无", thesis(), dossier());

        assertTrue(issues.contains("REPORT_TOO_SHORT"));
        assertTrue(issues.contains("INVALID_EVIDENCE_REF:E99"));
    }

    @Test
    void repairsMalformedNarrativeOnceUsingArrayFieldContracts() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(120000), eq(7000)))
                .thenReturn("{\"executiveSummary\":\"结构错误\",\"subQuestionAnalysis\":\"不是数组\"}")
                .thenReturn(narrativeJson());
        ResearchReportNarrativeAgent agent = new ResearchReportNarrativeAgent(llm);

        ResearchReportNarrative result = agent.generate(thesis(), blueprint(), dossier());

        assertTrue(result.isRepaired());
        verify(llm, times(2)).complete(anyString(), anyString(), eq(120000), eq(7000));
    }

    private ResearchThesis thesis() {
        ResearchThesis value = new ResearchThesis();
        value.setQuestion("长鑫科技上市两日的市值和交易表现说明什么？");
        value.setSubjectName("长鑫科技");
        value.setSubjectType("COMPANY");
        return value;
    }

    private ResearchReportBlueprint blueprint() {
        ResearchReportBlueprint value = new ResearchReportBlueprint();
        value.setDirectAnswer("两日高市值与高成交首先说明稀缺流通结构下的集中价格发现，不能直接等同于长期价值确认。");
        value.setDirection("MIXED");
        value.setConfidence("MEDIUM");
        value.setConfidenceBasis("交易事实可交叉验证，但观察期短且缺少长期经营数据。");
        value.setTimeRange("上市首日至第二个交易日");
        value.setDefinitions(Collections.singletonList("总市值需要与自由流通市值分开理解"));
        value.setExcludedQuestions(Collections.singletonList("长期盈利能力是否兑现"));
        for (int index = 1; index <= 3; index++) {
            ResearchReportBlueprint.KeyInsight insight = new ResearchReportBlueprint.KeyInsight();
            insight.setFinding("关键发现 " + index);
            insight.setMeaning("该发现对市值和交易解释的含义 " + index);
            insight.setEvidenceRefs(Collections.singletonList(index == 3 ? "E2" : "E1"));
            value.getKeyInsights().add(insight);
            ResearchReportBlueprint.SubQuestion sub = new ResearchReportBlueprint.SubQuestion();
            sub.setKey("question_" + index);
            sub.setQuestion("动态子问题 " + index);
            sub.setAnswer("当前回答 " + index);
            sub.setEvidenceRefs(Collections.singletonList(index == 3 ? "E2" : "E1"));
            sub.setCounterEvidenceRefs(Collections.<String>emptyList());
            sub.setImpact("影响总判断 " + index);
            sub.setUnknowns(Collections.singletonList("未知项 " + index));
            value.getSubQuestions().add(sub);
        }
        for (int index = 1; index <= 2; index++) {
            ResearchReportBlueprint.ArgumentChain chain = new ResearchReportBlueprint.ArgumentChain();
            chain.setFact("对象特定事实 " + index);
            chain.setInference("机制推理 " + index);
            chain.setJudgment("阶段判断 " + index);
            chain.setAlternativeExplanation("替代解释 " + index);
            chain.setEvidenceRefs(Collections.singletonList("E" + index));
            value.getArgumentChains().add(chain);
        }
        ResearchReportBlueprint.Counterargument counter = new ResearchReportBlueprint.Counterargument();
        counter.setClaim("成交活跃可能主要来自短期投机");
        counter.setEvidenceRefs(Collections.singletonList("E2"));
        counter.setResponse("需要观察成交衰减和价格稳定性");
        counter.setBecomesDominantWhen(Collections.singletonList("缩量持续下跌"));
        value.setStrongestCounterargument(counter);
        value.setKnowledgeTakeaways(Arrays.asList("总市值不等于可交易资金规模", "两日行情不能证明长期价值"));
        value.setUnknowns(Collections.singletonList("稳定换手中枢"));
        return value;
    }

    private List<ResearchEvidenceDossier> dossier() {
        Article one = article(1L, "交易所", "首日成交额创纪录", "流通盘较小且成交集中");
        Article two = article(2L, "财经媒体", "次日换手维持高位", "价格波动和分歧扩大");
        return new ResearchEvidenceDossierBuilder().build(Arrays.asList(
                new ResearchEvidenceCard(one, null, "SUPPORT", 92, "首日成交额显著上升"),
                new ResearchEvidenceCard(two, null, "COUNTER", 88, "高换手显示定价分歧")));
    }

    private Article article(Long id, String source, String title, String summary) {
        Article value = new Article();
        value.setId(id); value.setSourceName(source); value.setTitle(title); value.setSummary(summary);
        value.setBody(summary + "，并解释市值口径与流通结构。");
        value.setPublishedAt(LocalDateTime.of(2026, 7, 29, 10, 0));
        value.setUrl("https://example.com/" + id);
        return value;
    }

    private String narrativeJson() {
        String paragraph = "长鑫科技上市后的交易事实需要同时从流通结构、价格发现和时间跨度三个层面理解。[E1]"
                + " 首日成交活跃说明市场关注度高，但总市值并不代表同等规模资金完成了交易。"
                + " 次日高换手则说明买卖双方仍在重新评估价格，短期数据不能直接外推长期盈利能力。[E2]";
        return "{\"executiveSummary\":\"" + paragraph + paragraph + "\","
                + "\"whatHappened\":\"" + paragraph + paragraph + "\","
                + "\"subQuestionAnalysis\":[\"" + paragraph + "\",\"" + paragraph + "\",\"" + paragraph + "\"],"
                + "\"argumentAnalysis\":[\"" + paragraph + paragraph + "\",\"" + paragraph + paragraph + "\"],"
                + "\"counterAnalysis\":\"" + paragraph + paragraph + "\","
                + "\"scenarioAnalysis\":[\"" + paragraph + "\",\"" + paragraph + "\",\"" + paragraph + "\"],"
                + "\"knowledgeSynthesis\":\"" + paragraph + paragraph + "\","
                + "\"monitoringPlan\":\"" + paragraph + "\"}";
    }
}
