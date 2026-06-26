package com.finscope.service.agent;

import com.finscope.domain.article.Article;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleInterpretationAgentTest {
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
        ArticleInterpretationAgent agent = new ArticleInterpretationAgent(llmClient);

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
    void fallsBackToDomainSpecificInterpretationWhenLlmIsDisabled() {
        ArticleInterpretationAgent agent = new ArticleInterpretationAgent(new DisabledClient());

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
        ArticleInterpretationAgent agent = new ArticleInterpretationAgent(new DisabledClient());

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
}
