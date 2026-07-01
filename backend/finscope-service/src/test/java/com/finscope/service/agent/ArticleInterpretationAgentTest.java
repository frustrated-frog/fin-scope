package com.finscope.service.agent;

import com.finscope.domain.article.Article;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.insight.InsightCardGenerator;
import com.finscope.service.topic.TopicExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleInterpretationAgentTest {
    private ArticleInterpretationAgent agent(LlmChatClient llmClient) {
        ArticleInterpretationAgent agent = new ArticleInterpretationAgent();
        ReflectionTestUtils.setField(agent, "llmChatClient", llmClient);
        ReflectionTestUtils.setField(agent, "agentRunRepository", null);
        ReflectionTestUtils.setField(agent, "topicExtractor", new TopicExtractor());
        ReflectionTestUtils.setField(agent, "insightCardGenerator", new InsightCardGenerator());
        return agent;
    }

    @Test
    void interpretsArticleWithStructuredLlmOutput() {
        CapturingLlmClient llmClient = new CapturingLlmClient("{\n"
                + "  \"contentType\":\"TECH_PRACTICE\",\n"
                + "  \"topicName\":\"Cloudflare 免费基础设施实践\",\n"
                + "  \"topicDescription\":\"拆解 Cloudflare Workers、D1、R2、Pages、KV 如何组成低成本 Serverless 基础设施。\",\n"
                + "  \"oneSentenceSummary\":\"这篇长帖把 Cloudflare 免费额度串成一套可落地的个人项目基础设施。\",\n"
                + "  \"coreEvent\":\"作者用 Workers、D1、R2、Pages 和 KV 组合替代传统服务器与对象存储。\",\n"
                + "  \"importance\":\"它提供了低成本独立开发和内容产品验证的工程样本。\",\n"
                + "  \"impactTargets\":[\"Cloudflare\",\"Workers\",\"D1\",\"R2\",\"Pages\",\"KV\"],\n"
                + "  \"keyTerms\":[\"Cloudflare\",\"Workers\",\"D1\",\"R2\",\"Pages\",\"KV\",\"Serverless\"],\n"
                + "  \"learningQuestions\":[\"免费额度的边界和限制是什么？\",\"这套部署方案适合哪些个人项目？\",\"长期迁移和供应商锁定风险如何控制？\"],\n"
                + "  \"confidence\":0.91\n"
                + "}");
        ArticleInterpretationAgent agent = agent(llmClient);

        ArticleInterpretation interpretation = agent.interpret(cloudflareArticle());

        assertEquals("LLM", interpretation.getSource());
        assertEquals("Cloudflare 免费基础设施实践", interpretation.getTopicName());
        assertTrue(interpretation.getTopicDescription().contains("Workers"));
        assertTrue(interpretation.getKeyTerms().contains("D1"));
        assertTrue(interpretation.getKeyTerms().contains("R2"));
        assertTrue(interpretation.getLearningQuestions().get(0).contains("免费额度"));
        assertTrue(interpretation.getLearningQuestions().stream().noneMatch(question -> question.contains("资产定价")));
        assertTrue(llmClient.userPrompt.contains("Cloudflare"));
        assertTrue(llmClient.userPrompt.contains("Workers"));
    }

    @Test
    void promptMarksFullTextEvidenceAndForbidsUnreadableClaims() {
        CapturingLlmClient llmClient = new CapturingLlmClient(validQuantInterpretationJson());
        ArticleInterpretationAgent agent = agent(llmClient);

        agent.interpret(quantLoopArticle());

        assertTrue(llmClient.userPrompt.contains("contentQuality: FULL_TEXT"));
        assertTrue(llmClient.userPrompt.contains("bodyLength: "));
        assertTrue(llmClient.userPrompt.contains("禁止输出“正文未抓取”"));
        assertTrue(llmClient.userPrompt.contains("基于已提供正文证据"));
    }

    @Test
    void promptMarksXArticleLinkOnlyBodyAsLinkOnlyEvidence() {
        CapturingLlmClient llmClient = new CapturingLlmClient("{\n"
                + "  \"contentType\":\"SOCIAL_POST\",\n"
                + "  \"topicName\":\"X Article链接待补抓\",\n"
                + "  \"topicDescription\":\"帖子只包含X Article链接，需补抓长文正文。\",\n"
                + "  \"oneSentenceSummary\":\"当前正文仅为X Article链接。\",\n"
                + "  \"coreEvent\":\"作者发布了一个X Article链接。\",\n"
                + "  \"importance\":\"应补抓全文后再解读。\",\n"
                + "  \"impactTargets\":[\"内容抓取\"],\n"
                + "  \"keyTerms\":[\"X Article\"],\n"
                + "  \"learningQuestions\":[\"如何补抓X Article全文？\"],\n"
                + "  \"confidence\":0.2\n"
                + "}");
        ArticleInterpretationAgent agent = agent(llmClient);

        agent.interpret(xArticleLinkOnlyPost());

        assertTrue(llmClient.userPrompt.contains("contentQuality: LINK_ONLY"));
        assertTrue(llmClient.userPrompt.contains("visibleBodyPreview: http://x.com/i/article/2067524770175057920"));
    }

    @Test
    void rejectsUnreadableLlmOutputWhenFullTextExists() {
        CapturingLlmClient llmClient = new CapturingLlmClient("{\n"
                + "  \"contentType\":\"SOCIAL_POST\",\n"
                + "  \"topicName\":\"RohOnChain发布X Article链接型市场帖子但正文内容未被抓取\",\n"
                + "  \"topicDescription\":\"当前抓取结果未包含文章实质内容。\",\n"
                + "  \"oneSentenceSummary\":\"RohOnChain发布一条指向X Article的市场类帖子，但因正文未抓取，暂无法判断具体观点。\",\n"
                + "  \"coreEvent\":\"X账号发布了市场类帖子，当前未提供文章正文。\",\n"
                + "  \"importance\":\"缺少正文内容，无法确认其对市场或投资决策的实质影响。\",\n"
                + "  \"impactTargets\":[\"加密资产投资者\"],\n"
                + "  \"keyTerms\":[\"X Article\",\"信息抓取缺失\"],\n"
                + "  \"learningQuestions\":[\"该X Article正文是否包含具体交易观点？\"],\n"
                + "  \"confidence\":0.31\n"
                + "}");
        ArticleInterpretationAgent agent = agent(llmClient);

        ArticleInterpretation interpretation = agent.interpret(quantLoopArticle());

        assertEquals("FALLBACK", interpretation.getSource());
        assertTrue(interpretation.getOneSentenceSummary().toLowerCase().contains("quant trading system"));
        assertFalse(allText(interpretation).contains("正文未抓取"));
        assertFalse(allText(interpretation).contains("未提供文章正文"));
    }

    @Test
    void fallsBackToQuantLoopInterpretationWhenLlmFails() {
        ArticleInterpretationAgent agent = agent(new FailingConfiguredClient());

        ArticleInterpretation interpretation = agent.interpret(quantLoopArticle());

        assertEquals("FALLBACK", interpretation.getSource());
        assertEquals("Loop Engineering Quant Trading System", interpretation.getTopicName());
        assertTrue(interpretation.getTopicDescription().contains("量化交易"));
        assertTrue(interpretation.getKeyTerms().contains("quant trading"));
        assertTrue(interpretation.getLearningQuestions().get(0).contains("信号"));
        assertTrue(interpretation.getLearningQuestions().stream().noneMatch(question -> question.contains("风险偏好")));
    }

    @Test
    void fallsBackToDomainSpecificInterpretationWhenLlmIsDisabled() {
        ArticleInterpretationAgent agent = agent(new DisabledClient());

        ArticleInterpretation interpretation = agent.interpret(cloudflareArticle());

        assertEquals("FALLBACK", interpretation.getSource());
        assertEquals("Cloudflare 免费基础设施实践", interpretation.getTopicName());
        assertTrue(interpretation.getTopicDescription().contains("Workers"));
        assertTrue(interpretation.getKeyTerms().contains("Cloudflare"));
        assertTrue(interpretation.getKeyTerms().contains("Workers"));
        assertTrue(interpretation.getLearningQuestions().get(0).contains("免费额度"));
        assertTrue(interpretation.getLearningQuestions().stream().noneMatch(question -> question.contains("风险偏好")));
    }

    @Test
    void fallsBackToOsintInterpretationInsteadOfFinancialTemplate() {
        ArticleInterpretationAgent agent = agent(new DisabledClient());

        ArticleInterpretation interpretation = agent.interpret(osintArticle());

        assertEquals("FALLBACK", interpretation.getSource());
        assertEquals("OSINT 开源情报工具实践", interpretation.getTopicName());
        assertTrue(interpretation.getTopicDescription().contains("公开信息检索"));
        assertTrue(interpretation.getKeyTerms().contains("OSINT"));
        assertTrue(interpretation.getKeyTerms().contains("信息安全"));
        assertTrue(interpretation.getLearningQuestions().get(0).contains("合法合规"));
        assertTrue(interpretation.getLearningQuestions().stream().noneMatch(question -> question.contains("资产定价")));
        assertTrue(interpretation.getLearningQuestions().stream().noneMatch(question -> question.contains("风险偏好")));
    }

    private Article cloudflareArticle() {
        Article article = Article.createFetched(null, "手动研究",
                "Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施",
                "https://x.com/justloveabit/status/2069292114794762335",
                LocalDateTime.of(2026, 6, 23, 13, 31),
                "你听说过 Cloudflare 吗？过去半年，我没买过一台服务器，没付过一分钱云费用。",
                "作者：loveabit(@justloveabit)\n"
                        + "正文：\n"
                        + "过去半年，我没买过一台服务器，没付过一分钱云费用，却跑着一整套互联网基础设施。\n\n"
                        + "用 Workers 写后端，用 D1 做数据库，用 R2 存文件，用 Pages 托管前端，用 KV 做缓存。\n\n"
                        + "Cloudflare 已经不是 CDN 公司了，它是一个全球部署的 Serverless 操作系统。");
        article.setCategory("技术工具");
        return article;
    }

    private Article osintArticle() {
        Article article = Article.createFetched(null, "手动研究",
                "网警同款开盒思路，查人查公司查设备，五个免费开源工具",
                "https://x.com/yhslgg/status/2068317116831510838",
                LocalDateTime.of(2026, 6, 24, 10, 0),
                "最近连续分享了好几个 OSINT 工具，今天就给兄弟们讲讲怎么组合用。",
                "作者：老杨啊(@yhslgg)\n"
                        + "正文：\n"
                        + "最近连续分享了好几个 OSINT 工具，今天就给兄弟们讲讲怎么组合用。\n\n"
                        + "五个工具覆盖查人、查公司、查设备场景，可以组合做公开信息检索、域名资产发现和网络暴露面排查。");
        article.setCategory("信息安全");
        return article;
    }

    private Article quantLoopArticle() {
        String paragraph = "I will break down exactly how to build the loops that run an entire quant trading system on their own. "
                + "The system pulls market data, generates signals, backtests them, verifies every signal through a separate agent, "
                + "executes only what passes verification, monitors risk, and writes lessons back to memory. "
                + "Loop engineering matters because quant trading is already a loop: pull data, generate signals, backtest, execute, monitor risk, repeat. ";
        StringBuilder body = new StringBuilder();
        body.append("作者：Roan(@RohOnChain)\n");
        body.append("发布时间：2026-06-22T21:54:52\n");
        body.append("互动：likes=1366，retweets=167，replies=43，views=1455728\n");
        body.append("正文：\n");
        for (int i = 0; i < 12; i++) {
            body.append(paragraph).append("\n\n");
        }
        Article article = Article.createFetched(null, "手动研究",
                "How To Use Loop Engineering To Build A Self-Improving Quant Trading System",
                "https://x.com/RohOnChain/status/2069056530960490835",
                LocalDateTime.of(2026, 6, 22, 21, 54, 52),
                "I will break down exactly how to build the loops that run an entire quant trading system on their own.",
                body.toString());
        article.setCategory("市场");
        return article;
    }

    private Article xArticleLinkOnlyPost() {
        Article article = Article.createFetched(null, "手动研究",
                "X 帖子 | @RohOnChain：http://x.com/i/article/2067524770175057920",
                "https://twitter.com/RohOnChain/status/2069056530960490835",
                LocalDateTime.of(2026, 6, 22, 21, 54, 52),
                "http://x.com/i/article/2067524770175057920",
                "作者：Roan(@RohOnChain)\n"
                        + "发布时间：2026-06-22T21:54:52\n"
                        + "互动：likes=98，retweets=6，replies=11\n"
                        + "正文：\n"
                        + "http://x.com/i/article/2067524770175057920");
        article.setCategory("市场");
        return article;
    }

    private String validQuantInterpretationJson() {
        return "{\n"
                + "  \"contentType\":\"SOCIAL_POST\",\n"
                + "  \"topicName\":\"Loop Engineering Quant Trading System\",\n"
                + "  \"topicDescription\":\"拆解如何用循环式 agent 流程搭建自我改进的量化交易系统。\",\n"
                + "  \"oneSentenceSummary\":\"文章说明量化交易本身就是数据、信号、回测、执行、风控和记忆复盘组成的自动化循环。\",\n"
                + "  \"coreEvent\":\"作者提出用 loop engineering 把量化研究和交易执行串成自我改进系统。\",\n"
                + "  \"importance\":\"它把 agent 从单次提示升级为持续运行的研究和执行流程。\",\n"
                + "  \"impactTargets\":[\"量化交易系统\",\"agent workflow\",\"风险控制\"],\n"
                + "  \"keyTerms\":[\"loop engineering\",\"quant trading\",\"backtest\",\"risk monitor\"],\n"
                + "  \"learningQuestions\":[\"如何验证每个信号？\",\"如何把交易教训写回记忆？\"],\n"
                + "  \"confidence\":0.88\n"
                + "}";
    }

    private String allText(ArticleInterpretation interpretation) {
        return String.join("\n",
                text(interpretation.getTopicName()),
                text(interpretation.getTopicDescription()),
                text(interpretation.getOneSentenceSummary()),
                text(interpretation.getCoreEvent()),
                text(interpretation.getImportance()),
                text(interpretation.getBackground()),
                text(interpretation.getFacts()),
                text(interpretation.getReasoning()),
                text(interpretation.getOpinions()));
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private static class CapturingLlmClient implements LlmChatClient {
        private final String response;
        private String userPrompt;

        CapturingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String modelName() {
            return "fake-model";
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.userPrompt = userPrompt;
            return response;
        }
    }

    private static class DisabledClient implements LlmChatClient {
        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public String modelName() {
            return "disabled";
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new AssertionError("Disabled client should not be called");
        }
    }

    private static class FailingConfiguredClient implements LlmChatClient {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String modelName() {
            return "failing-model";
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) throws Exception {
            throw new java.net.SocketTimeoutException("Read timed out");
        }
    }
}
