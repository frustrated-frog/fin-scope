package com.finscope.service.insight;

import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightCardGeneratorTest {
    @Test
    void generatesFixedFormatInvestmentCardFromArticle() {
        Article article = Article.createFetched(7L, "手动研究", "美联储暗示降息 黄金ETF获得资金流入",
                "https://example.com/fed-gold-etf",
                LocalDateTime.of(2026, 6, 24, 9, 30),
                "美联储官员释放偏鸽信号，黄金ETF出现连续资金流入。",
                "美联储官员释放偏鸽信号，市场开始交易降息预期。黄金ETF出现连续资金流入，美元指数回落。");
        article.setId(42L);
        article.setCategory("宏观");
        article.setNoveltyType("NEW");
        article.setNoveltyReason("首次进入信息流");

        InsightCard card = new InsightCardGenerator().generate(article);

        assertEquals(42L, card.getArticleId());
        assertEquals("美联储暗示降息 黄金ETF获得资金流入", card.getTitle());
        assertTrue(card.getOneSentenceSummary().contains("美联储"));
        assertTrue(card.getCoreEvent().contains("降息"));
        assertTrue(card.getImportance().contains("利率预期"));
        assertTrue(card.getImpactTargets().contains("黄金"));
        assertTrue(card.getFollowUpQuestions().contains("下一次"));
        assertTrue(card.getCardMarkdown().contains("## 情报卡片"));
        assertTrue(card.getCardMarkdown().contains("### 为什么重要"));
    }

    @Test
    void generatesResearchPaperCardFromArxivRssContent() {
        Article article = Article.createFetched(8L, "arXiv cs.AI", "RIFT-Bench: Dynamic Red-teaming For Agentic AI Systems",
                "https://arxiv.org/abs/2606.23927",
                LocalDateTime.of(2026, 6, 24, 12, 0),
                "arXiv:2606.23927v1 Announce Type: new Abstract: Agentic AI systems powered by large language models are rapidly evolving into autonomous decision-making systems.",
                "作者：Yarin Yerushalmi Levi、Roy Betser\n"
                        + "分类：cs.AI\n"
                        + "摘要：arXiv:2606.23927v1 Announce Type: new Abstract: Agentic AI systems powered by large language models are rapidly evolving into autonomous decision-making systems, exposing attack vectors beyond traditional LLM vulnerabilities. We introduce RIFT-Bench, a graph representation-driven methodology for dynamic red-teaming.");
        article.setId(43L);
        article.setCategory("技术研究");

        InsightCard card = new InsightCardGenerator().generate(article);

        assertTrue(card.getOneSentenceSummary().contains("论文"));
        assertTrue(card.getOneSentenceSummary().contains("RIFT-Bench"));
        assertTrue(card.getCoreEvent().contains("作者"));
        assertTrue(card.getCoreEvent().contains("cs.AI"));
        assertTrue(card.getImportance().contains("Agent"));
        assertTrue(card.getImportance().contains("安全评测"));
        assertTrue(card.getImpactTargets().contains("RIFT-Bench"));
        assertTrue(card.getImpactTargets().contains("Agentic AI"));
        assertTrue(card.getFollowUpQuestions().contains("复现"));
        assertFalse(card.getImportance().contains("短期市场情绪"));
    }

    @Test
    void generatesSocialPostCardFromXArticleContent() {
        Article article = Article.createFetched(null, "手动研究", "Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施",
                "https://x.com/justloveabit/status/2069292114794762335",
                LocalDateTime.of(2026, 6, 23, 13, 31),
                "你听说过 Cloudflare 吗？过去半年，我没买过一台服务器，没付过一分钱云费用。",
                "作者：loveabit(@justloveabit)\n"
                        + "发布时间：2026-06-23T13:31\n"
                        + "互动：likes=171，retweets=33，replies=1，views=19490\n"
                        + "正文：\n"
                        + "过去半年，我没买过一台服务器，没付过一分钱云费用，却跑着一整套互联网基础设施。\n\n"
                        + "比如：用 Workers 写后端 → 用 D1 做数据库 → 用 R2 存文件 → 用 Pages 托管前端 → 用 KV 做缓存。\n\n"
                        + "Cloudflare 已经不是 CDN 公司了，它是一个 全球部署的 Serverless 操作系统。");
        article.setId(44L);
        article.setCategory("技术工具");

        InsightCard card = new InsightCardGenerator().generate(article);

        assertTrue(card.getOneSentenceSummary().contains("Cloudflare"));
        assertTrue(card.getCoreEvent().contains("@justloveabit"));
        assertTrue(card.getImportance().contains("开发者基础设施"));
        assertTrue(card.getImpactTargets().contains("Cloudflare"));
        assertTrue(card.getImpactTargets().contains("Workers"));
        assertTrue(card.getImpactTargets().contains("D1"));
        assertTrue(card.getFollowUpQuestions().contains("免费额度"));
        assertFalse(card.getImportance().contains("短期市场情绪"));
    }

    @Test
    void generatesOsintSocialPostCardWithoutCloudflareTemplate() {
        Article article = Article.createFetched(null, "手动研究", "网警同款开盒思路，查人查公司查设备，五个免费开源工具",
                "https://x.com/yhslgg/status/2068317116831510838",
                LocalDateTime.of(2026, 6, 24, 10, 0),
                "最近连续分享了好几个 OSINT 工具，今天就给兄弟们讲讲怎么组合用。",
                "作者：老杨啊(@yhslgg)\n"
                        + "正文：\n"
                        + "最近连续分享了好几个 OSINT 工具，今天就给兄弟们讲讲怎么组合用。\n\n"
                        + "五个工具覆盖查人、查公司、查设备场景，可以组合做公开信息检索、域名资产发现和网络暴露面排查。");
        article.setId(45L);
        article.setCategory("信息安全");

        InsightCard card = new InsightCardGenerator().generate(article);

        assertTrue(card.getImportance().contains("OSINT"));
        assertTrue(card.getImportance().contains("合规边界"));
        assertTrue(card.getImpactTargets().contains("OSINT"));
        assertTrue(card.getImpactTargets().contains("信息安全"));
        assertTrue(card.getFollowUpQuestions().contains("合法合规"));
        assertFalse(card.getFollowUpQuestions().contains("免费额度"));
        assertFalse(card.getImpactTargets().contains("社媒实践经验"));
    }
}
