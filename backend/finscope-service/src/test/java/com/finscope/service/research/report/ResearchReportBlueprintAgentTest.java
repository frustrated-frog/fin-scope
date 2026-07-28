package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ResearchReportBlueprintAgentTest {
    @Test
    void generatesStrictQuestionSpecificBlueprintWithinBudget() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000))).thenReturn(validJson());
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        ResearchReportBlueprint result = agent.generate(thesis(), dossier());

        assertEquals("上市首日的高市值主要反映稀缺流通结构和集中定价，不能单独证明长期价值。", result.getDirectAnswer());
        assertEquals(3, result.getKeyInsights().size());
        assertEquals(3, result.getSubQuestions().size());
        assertEquals(2, result.getArgumentChains().size());
        verify(llm).complete(anyString(), anyString(), eq(90000), eq(3000));
    }

    @Test
    void rejectsReferencesOutsideTheEvidenceDossier() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000)))
                .thenReturn(validJson().replace("\"E2\"", "\"E99\""));
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        try {
            agent.generate(thesis(), dossier());
        } catch (ResearchReportGenerationException ex) {
            assertTrue(ex.getMessage().contains("INVALID_EVIDENCE_REF"));
            return;
        }
        throw new AssertionError("invalid evidence reference should be rejected");
    }

    @Test
    void repairsMalformedBlueprintOnceUsingTheExactNestedContract() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), eq(90000), eq(3000)))
                .thenReturn("{\"directAnswer\":\"结构错误\",\"keyInsights\":\"不是数组\"}")
                .thenReturn(validJson());
        ResearchReportBlueprintAgent agent = new ResearchReportBlueprintAgent(llm,
                new ResearchReportBlueprintValidator());

        ResearchReportBlueprint result = agent.generate(thesis(), dossier());

        assertTrue(result.isRepaired());
        verify(llm, times(2)).complete(anyString(), anyString(), eq(90000), eq(3000));
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

    private String validJson() {
        return "{"
                + "\"directAnswer\":\"上市首日的高市值主要反映稀缺流通结构和集中定价，不能单独证明长期价值。\","
                + "\"direction\":\"MIXED\",\"confidence\":\"MEDIUM\","
                + "\"confidenceBasis\":\"两项独立交易事实相互验证，但缺少更长观察期。\","
                + "\"timeRange\":\"上市首日至第二个交易日\","
                + "\"definitions\":[\"区分总市值与自由流通市值\"],"
                + "\"excludedQuestions\":[\"长期盈利能力\"],"
                + "\"keyInsights\":["
                + insight("高总市值", "不能脱离流通结构解释", "E1") + ","
                + insight("成交活跃", "市场关注与分歧同时升高", "E1", "E2") + ","
                + insight("换手维持高位", "短期价格发现尚未结束", "E2") + "],"
                + "\"subQuestions\":["
                + sub("valuation", "市值应如何理解？", "需结合流通结构", "E1") + ","
                + sub("trading", "成交说明什么？", "关注度与分歧并存", "E1", "E2") + ","
                + sub("duration", "两日数据能否外推？", "不能直接外推长期价值", "E2") + "],"
                + "\"argumentChains\":["
                + chain("流通盘约束定价", "稀缺筹码放大价格弹性", "高市值含结构性溢价", "盈利预期也可能贡献", "E1") + ","
                + chain("高换手持续", "买卖双方分歧仍大", "价格发现尚未稳定", "单纯获利回吐", "E2") + "],"
                + "\"strongestCounterargument\":{\"claim\":\"高成交也可能只是短期投机\",\"evidenceRefs\":[\"E2\"],\"response\":\"需要后续成交衰减和价格稳定性验证\",\"becomesDominantWhen\":[\"成交快速萎缩且价格持续回落\"]},"
                + "\"scenarios\":[{\"name\":\"基准\",\"trigger\":\"换手逐步下降\",\"mechanism\":\"价格发现趋稳\",\"observableResult\":\"波动收敛\",\"impact\":\"维持混合判断\",\"evidenceRefs\":[\"E2\"]}],"
                + "\"knowledgeTakeaways\":[\"总市值不等于可交易资金规模\"],"
                + "\"unknowns\":[\"稳定换手中枢\"],"
                + "\"watchItems\":[{\"metric\":\"换手率\",\"baseline\":\"上市初期高位\",\"frequency\":\"每日\",\"upgradeCondition\":\"量价稳定\",\"downgradeCondition\":\"缩量持续下跌\"}]"
                + "}";
    }

    private String insight(String finding, String meaning, String... refs) {
        return "{\"finding\":\"" + finding + "\",\"meaning\":\"" + meaning
                + "\",\"evidenceRefs\":" + refs(refs) + "}";
    }

    private String sub(String key, String question, String answer, String... refs) {
        return "{\"key\":\"" + key + "\",\"question\":\"" + question + "\",\"answer\":\""
                + answer + "\",\"evidenceRefs\":" + refs(refs)
                + ",\"counterEvidenceRefs\":[],\"impact\":\"影响总判断\",\"unknowns\":[]}";
    }

    private String chain(String fact, String inference, String judgment, String alternative, String... refs) {
        return "{\"fact\":\"" + fact + "\",\"inference\":\"" + inference + "\",\"judgment\":\""
                + judgment + "\",\"alternativeExplanation\":\"" + alternative + "\",\"evidenceRefs\":" + refs(refs) + "}";
    }

    private String refs(String... refs) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < refs.length; index++) {
            if (index > 0) out.append(',');
            out.append('\"').append(refs[index]).append('\"');
        }
        return out.append(']').toString();
    }
}
