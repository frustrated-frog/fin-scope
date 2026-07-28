package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
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
}
