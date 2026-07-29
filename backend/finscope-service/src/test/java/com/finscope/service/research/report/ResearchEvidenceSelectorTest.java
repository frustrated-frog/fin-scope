package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ResearchSearchEvidence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEvidenceSelectorTest {
    private final ResearchEvidenceSelector selector = new ResearchEvidenceSelector();

    @Test
    void rejectsUnrelatedAiPapersAndKeepsSemiconductorCycleEvidence() {
        ResearchThesis thesis = thesis();
        List<Article> articles = new ArrayList<Article>();
        articles.add(article(1L, "产业媒体", "半导体设备订单继续增长，晶圆厂上调资本开支", "设备需求维持高景气"));
        articles.add(article(2L, "论文源", "Communication-Efficient Digital-Twin Coordination for LLM Agents", "AI 论文摘要"));

        List<ResearchEvidenceCard> selected = selector.select(thesis, articles, Collections.emptyList());

        assertEquals(1, selected.size());
        assertEquals(1L, selected.get(0).getArticle().getId());
        assertEquals("SUPPORT", selected.get(0).getStance());
    }

    @Test
    void capsReportEvidenceAndSourceConcentrationWhileKeepingCounterEvidence() {
        List<Article> articles = new ArrayList<Article>();
        for (long id = 1; id <= 20; id++) {
            String source = id <= 10 ? "单一聚合源" : "来源" + id;
            String title = id == 20
                    ? "半导体设备订单下滑，晶圆厂削减资本开支"
                    : "半导体设备订单增长，产业周期景气延续 " + id;
            articles.add(article(id, source, title, "半导体设备产业跟踪"));
        }

        List<ResearchEvidenceCard> selected = selector.select(thesis(), articles, Collections.emptyList());

        assertTrue(selected.size() <= 15);
        assertTrue(selected.stream().filter(card -> "单一聚合源".equals(card.getArticle().getSourceName())).count() <= 5);
        assertTrue(selected.stream().anyMatch(card -> "COUNTER".equals(card.getStance())));
    }

    @Test
    void reportsWhyEvidenceIsInsufficientButNeverBlocksReportGeneration() {
        List<ResearchEvidenceCard> selected = selector.select(thesis(), Collections.singletonList(
                article(1L, "单一来源", "半导体设备订单增长", "产业景气")), Collections.emptyList());

        EvidenceSufficiency result = EvidenceSufficiency.assess(selected);

        assertFalse(result.isSufficient());
        assertTrue(result.getWarnings().stream().anyMatch(item -> item.contains("证据数量")));
    }

    @Test
    void usesControlledSearchIntentWhenRelevantEvidenceHasNoExplicitStanceWords() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(2L);
        thesis.setQuestion("长鑫科技上市后的交易表现说明了什么？");
        thesis.setSubjectType("COMPANY");
        thesis.setSubjectName("长鑫科技");
        List<Article> articles = new ArrayList<Article>();
        articles.add(article(1L, "Google News · 支持证据搜索",
                "长鑫科技上市首日成交额破千亿元", "首日交易数据汇总"));
        articles.add(article(2L, "Google News · 反方证据搜索",
                "长鑫科技上市后投资者担忧估值", "市场分歧仍然明显"));

        List<ResearchEvidenceCard> selected = selector.select(thesis, articles, Collections.emptyList());

        assertEquals(2, selected.size());
        assertTrue(selected.stream().anyMatch(card -> "SUPPORT".equals(card.getStance())));
        assertTrue(selected.stream().anyMatch(card -> "COUNTER".equals(card.getStance())));
    }

    @Test
    void includesRunScopedTavilyEvidenceWithoutRequiringAnArticleRecord() {
        ResearchSearchEvidence searchEvidence = new ResearchSearchEvidence();
        searchEvidence.setId(71L);
        searchEvidence.setResearchRunId(18L);
        searchEvidence.setIntent("SUPPORT");
        searchEvidence.setTitle("长鑫科技上市后扩大存储芯片研发投入");
        searchEvidence.setContent("公司上市融资将用于先进制程研发与产能建设，对产业链形成正面影响。");
        searchEvidence.setUrl("https://exchange.example.com/disclosure/71");
        searchEvidence.setSourceDomain("exchange.example.com");
        searchEvidence.setSourceTier("T1");
        searchEvidence.setRelevanceScore(0.96D);

        List<ResearchEvidenceCard> selected = selector.select(thesisForChangxin(),
                Collections.<Article>emptyList(), Collections.emptyList(),
                Collections.singletonList(searchEvidence));

        assertEquals(1, selected.size());
        assertEquals("SUPPORT", selected.get(0).getStance());
        assertEquals("T1", selected.get(0).getSourceTier());
        assertEquals("exchange.example.com", selected.get(0).getSourceIdentity());
        assertEquals(null, selected.get(0).getArticle().getId());
    }

    @Test
    void acceptsHighConfidenceCrossLanguageTavilyEvidenceForChineseCompany() {
        ResearchSearchEvidence searchEvidence = searchEvidence(
                "China memory chipmaker CXMT skyrockets 470% in Shanghai debut",
                "CXMT raised fresh capital in its IPO and its market debut changed the competitive landscape.",
                0.478D);

        List<ResearchEvidenceCard> selected = selector.select(thesisForChangxin(),
                Collections.<Article>emptyList(), Collections.emptyList(),
                Collections.singletonList(searchEvidence));

        assertEquals(1, selected.size());
        assertTrue(selected.get(0).getRelevanceScore() >= 40);
    }

    @Test
    void rejectsLowConfidenceTavilyResultWithoutAnyEntityMatch() {
        ResearchSearchEvidence searchEvidence = searchEvidence(
                "Jialicheng IPO opens at 84.46 yuan",
                "The PCB prototyping company raised nearly 4.7 billion yuan in a separate listing.",
                0.124D);

        List<ResearchEvidenceCard> selected = selector.select(thesisForChangxin(),
                Collections.<Article>emptyList(), Collections.emptyList(),
                Collections.singletonList(searchEvidence));

        assertTrue(selected.isEmpty());
    }

    @Test
    void removesSearchRankingMarkersFromSelectedClaims() {
        ResearchSearchEvidence searchEvidence = searchEvidence(
                "长鑫科技上市次日市值回落",
                "[S4] - 01/[长鑫科技上市次日市值回落，融资余额仍处高位",
                0.87D);

        List<ResearchEvidenceCard> selected = selector.select(thesisForChangxin(),
                Collections.<Article>emptyList(), Collections.emptyList(),
                Collections.singletonList(searchEvidence));

        assertEquals(1, selected.size());
        assertEquals("长鑫科技上市次日市值回落，融资余额仍处高位", selected.get(0).getClaim());
    }

    @Test
    void prefersConciseSearchSnippetOverFetchedPageNavigation() {
        ResearchSearchEvidence searchEvidence = searchEvidence(
                "长鑫科技上市次日市值回落",
                "[S4] - 01/[其他市场导航](https://example.com/other) - 02/广告与站点菜单",
                0.87D);
        searchEvidence.setSearchSnippet("长鑫科技上市首日成交额1411.87亿元，换手率66.40%。");

        List<ResearchEvidenceCard> selected = selector.select(thesisForChangxin(),
                Collections.<Article>emptyList(), Collections.emptyList(),
                Collections.singletonList(searchEvidence));

        assertEquals(1, selected.size());
        assertEquals("长鑫科技上市首日成交额1411.87亿元，换手率66.40%。", selected.get(0).getClaim());
    }

    @Test
    void keepsACompleteSelectedFactParagraphWithoutEllipsis() {
        ResearchSearchEvidence searchEvidence = searchEvidence(
                "长鑫科技上市交易数据",
                "长鑫科技上市首日完成集中价格发现。" + repeat("交易保持活跃，", 60) + "最后一句完整结论。",
                0.87D);

        ResearchEvidenceCard selected = selector.select(thesisForChangxin(),
                Collections.<Article>emptyList(), Collections.emptyList(),
                Collections.singletonList(searchEvidence)).get(0);

        assertTrue(selected.getClaim().contains("最后一句完整结论。"));
        assertFalse(selected.getClaim().contains("…"));
        assertFalse(selected.getClaim().contains("（已截断）"));
    }

    private ResearchSearchEvidence searchEvidence(String title, String content, double score) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setId(72L);
        value.setResearchRunId(19L);
        value.setIntent("SUPPORT");
        value.setQueryText("长鑫科技上市 半导体产业 竞争格局 影响");
        value.setTitle(title);
        value.setContent(content);
        value.setUrl("https://www.cnbc.com/cxmt-ipo.html");
        value.setSourceDomain("www.cnbc.com");
        value.setSourceTier("T2");
        value.setRelevanceScore(score);
        return value;
    }

    private ResearchThesis thesisForChangxin() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(2L);
        thesis.setQuestion("长鑫科技上市带来了哪些影响？");
        thesis.setSubjectType("COMPANY");
        thesis.setSubjectName("长鑫科技");
        return thesis;
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(1L);
        thesis.setQuestion("科技板块冲高后近期大跌回落，周期是否还能持续");
        thesis.setSubjectType("INDUSTRY");
        thesis.setSubjectName("半导体设备");
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


    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
