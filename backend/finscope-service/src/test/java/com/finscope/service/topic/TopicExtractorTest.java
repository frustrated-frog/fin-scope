package com.finscope.service.topic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicExtractorTest {
    @Test
    void extractsFinancialTermsAndLearningQuestionsFromText() {
        TopicExtraction extraction = new TopicExtractor().extract(
                "美联储释放降息信号，黄金价格走强，市场关注美债收益率变化。");

        assertEquals("美联储", extraction.getPrimaryTopicName());
        assertTrue(extraction.getTerms().contains("美联储"));
        assertTrue(extraction.getTerms().contains("降息"));
        assertTrue(extraction.getTerms().contains("黄金"));
        assertTrue(extraction.getTerms().contains("美债"));
        assertTrue(extraction.getLearningQuestions().get(0).contains("美联储"));
    }

    @Test
    void fallbackCreatesReadableTechnicalTopicInsteadOfTruncatedTitle() {
        TopicExtraction extraction = new TopicExtractor().extract(
                "Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施。"
                        + "过去半年没有买服务器，用 Workers 写后端，用 D1 做数据库，用 R2 存文件，"
                        + "用 Pages 托管前端，用 KV 做缓存。Cloudflare 像一个全球部署的 Serverless 操作系统。");

        assertEquals("Cloudflare 免费基础设施实践", extraction.getPrimaryTopicName());
        assertTrue(extraction.getDescription().contains("Workers"));
        assertTrue(extraction.getTerms().contains("Cloudflare"));
        assertTrue(extraction.getTerms().contains("Workers"));
        assertTrue(extraction.getTerms().contains("D1"));
        assertTrue(extraction.getTerms().contains("R2"));
        assertTrue(extraction.getLearningQuestions().get(0).contains("免费额度"));
        assertTrue(extraction.getLearningQuestions().get(1).contains("部署"));
        assertTrue(extraction.getLearningQuestions().stream().noneMatch(question -> question.contains("资产定价")));
        assertTrue(extraction.getLearningQuestions().stream().noneMatch(question -> question.contains("风险偏好")));
    }
}
