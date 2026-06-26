package com.finscope.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.insight.InsightCardGenerator;
import com.finscope.service.topic.TopicExtraction;
import com.finscope.service.topic.TopicExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ArticleInterpretationAgent {
    private static final String NODE_NAME = "article-interpret";
    private final LlmChatClient llmChatClient;
    private final AgentRunRepository agentRunRepository;
    private final TopicExtractor topicExtractor;
    private final InsightCardGenerator insightCardGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ArticleInterpretationAgent(LlmChatClient llmChatClient) {
        this(llmChatClient, null, new TopicExtractor(), new InsightCardGenerator());
    }

    @Autowired
    public ArticleInterpretationAgent(LlmChatClient llmChatClient,
                                      AgentRunRepository agentRunRepository,
                                      TopicExtractor topicExtractor,
                                      InsightCardGenerator insightCardGenerator) {
        this.llmChatClient = llmChatClient;
        this.agentRunRepository = agentRunRepository;
        this.topicExtractor = topicExtractor;
        this.insightCardGenerator = insightCardGenerator;
    }

    public boolean isConfigured() {
        return llmChatClient != null && llmChatClient.isConfigured();
    }

    public ArticleInterpretation interpret(Article article) {
        long start = System.currentTimeMillis();
        String userPrompt = buildUserPrompt(article);
        if (!isConfigured()) {
            ArticleInterpretation fallback = fallback(article);
            record("FALLBACK", traceInput(article, userPrompt), traceOutput(fallback), null, start);
            return fallback;
        }
        try {
            String raw = llmChatClient.complete(systemPrompt(), userPrompt);
            ArticleInterpretation interpretation = parse(raw, article);
            record("SUCCESS", traceInput(article, userPrompt), raw, null, start);
            return interpretation;
        } catch (Exception ex) {
            record("FAILED", traceInput(article, userPrompt), null, ex.getMessage(), start);
            return fallback(article);
        }
    }

    private ArticleInterpretation parse(String raw, Article article) throws Exception {
        String json = extractJson(raw);
        JsonNode root = objectMapper.readTree(json);
        ArticleInterpretation fallback = fallback(article);
        ArticleInterpretation interpretation = new ArticleInterpretation();
        interpretation.setSource("LLM");
        interpretation.setRawJson(json);

        // 基础字段
        interpretation.setContentType(text(root, "contentType", fallback.getContentType()));
        interpretation.setTopicName(text(root, "topicName", fallback.getTopicName()));
        interpretation.setTopicDescription(text(root, "topicDescription", fallback.getTopicDescription()));
        interpretation.setOneSentenceSummary(text(root, "oneSentenceSummary", fallback.getOneSentenceSummary()));
        interpretation.setCoreEvent(text(root, "coreEvent", fallback.getCoreEvent()));
        interpretation.setImportance(text(root, "importance", fallback.getImportance()));
        interpretation.setImpactTargets(list(root.get("impactTargets"), fallback.getImpactTargets()));
        interpretation.setKeyTerms(list(root.get("keyTerms"), fallback.getKeyTerms()));
        interpretation.setLearningQuestions(list(root.get("learningQuestions"), fallback.getLearningQuestions()));
        interpretation.setConfidence(root.path("confidence").asDouble(fallback.getConfidence()));

        // 深度解读字段
        interpretation.setBackground(text(root, "background", ""));
        interpretation.setKeyData(text(root, "keyData", ""));
        interpretation.setTimeline(text(root, "timeline", ""));
        interpretation.setRelatedParties(text(root, "relatedParties", ""));
        interpretation.setRiskFactors(text(root, "riskFactors", ""));
        interpretation.setFutureOutlook(text(root, "futureOutlook", ""));
        interpretation.setImpactOnInvestment(text(root, "impactOnInvestment", ""));
        interpretation.setImpactOnStartup(text(root, "impactOnStartup", ""));
        interpretation.setProfessionalInsight(text(root, "professionalInsight", ""));
        interpretation.setFacts(text(root, "facts", ""));
        interpretation.setReasoning(text(root, "reasoning", ""));
        interpretation.setOpinions(text(root, "opinions", ""));

        validate(interpretation, fallback);
        return interpretation;
    }

    private void validate(ArticleInterpretation interpretation, ArticleInterpretation fallback) {
        if (isBlank(interpretation.getTopicName())) {
            interpretation.setTopicName(fallback.getTopicName());
        }
        if (isBlank(interpretation.getTopicDescription())) {
            interpretation.setTopicDescription(fallback.getTopicDescription());
        }
        if (isBlank(interpretation.getOneSentenceSummary())) {
            interpretation.setOneSentenceSummary(fallback.getOneSentenceSummary());
        }
        if (isBlank(interpretation.getCoreEvent())) {
            interpretation.setCoreEvent(fallback.getCoreEvent());
        }
        if (isBlank(interpretation.getImportance())) {
            interpretation.setImportance(fallback.getImportance());
        }
        if (interpretation.getImpactTargets() == null || interpretation.getImpactTargets().isEmpty()) {
            interpretation.setImpactTargets(fallback.getImpactTargets());
        }
        if (interpretation.getKeyTerms() == null || interpretation.getKeyTerms().isEmpty()) {
            interpretation.setKeyTerms(fallback.getKeyTerms());
        }
        if (interpretation.getLearningQuestions() == null || interpretation.getLearningQuestions().isEmpty()) {
            interpretation.setLearningQuestions(fallback.getLearningQuestions());
        }
    }

    private ArticleInterpretation fallback(Article article) {
        TopicExtraction topic = topicExtractor.extract(join(" ", article.getTitle(), article.getSummary(), article.getBody()));
        InsightCard card = insightCardGenerator.generate(article);
        ArticleInterpretation interpretation = new ArticleInterpretation();
        interpretation.setSource("FALLBACK");
        interpretation.setContentType(contentType(article));
        interpretation.setTopicName(topic.getPrimaryTopicName());
        interpretation.setTopicDescription(topic.getDescription());
        interpretation.setOneSentenceSummary(card.getOneSentenceSummary());
        interpretation.setCoreEvent(card.getCoreEvent());
        interpretation.setImportance(card.getImportance());
        interpretation.setImpactTargets(splitTargets(card.getImpactTargets()));
        interpretation.setKeyTerms(topic.getTerms());
        interpretation.setLearningQuestions(topic.getLearningQuestions());
        interpretation.setConfidence(0.45);
        return interpretation;
    }

    private String systemPrompt() {
        return "你是 FinScope 的资深金融分析师和投研专家。你的任务是对抓取的文章进行深度解读,生成结构化的投研分析卡片。"
                + "分析需要包含:事件概述、背景脉络、关键数据、时间线、相关方、风险因素、未来展望、对投资和创业的影响等维度。"
                + "同时要区分事实、推理和观点,提供专业的独立判断。"
                + "只返回 JSON,不返回 Markdown,不要包裹代码块。";
    }

    private String buildUserPrompt(Article article) {
        return "请深度解读下面文章,输出JSON对象。\n\n"
                + "【字段说明】\n"
                + "基础字段: contentType, topicName, topicDescription, oneSentenceSummary, coreEvent, importance, impactTargets, keyTerms, learningQuestions, confidence\n"
                + "深度解读字段(参考每日金融投资创业简报的深度分析风格):\n"
                + "- background: 事件背景是什么(150字内,提供必要的上下文脉络)\n"
                + "- keyData: 关键数据(列举2-5个核心数据,如金额、增长率、市场规模等)\n"
                + "- timeline: 时间线(关键时间节点,用分号分隔,如: '2024年Q1:公司成立;2024年Q3:完成A轮融资')\n"
                + "- relatedParties: 相关方(涉及的机构、企业、人物等,用分号分隔)\n"
                + "- riskFactors: 风险因素(列举2-3个潜在风险)\n"
                + "- futureOutlook: 未来展望(事件后续可能的发展方向,150字内)\n"
                + "- impactOnInvestment: 对投资的影响(从投资角度分析机会与风险,200字内)\n"
                + "- impactOnStartup: 对创业的影响(对创业者有哪些启示或影响,200字内)\n"
                + "- professionalInsight: 专业解读(综合分析,体现独立思考和专业判断,300字内)\n"
                + "- facts: 事实(文中明确陈述的客观事实,100字内)\n"
                + "- reasoning: 推理(基于事实的合理推断,100字内)\n"
                + "- opinions: 观点(你的独立判断和专业观点,100字内)\n\n"
                + "【要求】\n"
                + "1. topicName 要像知识库主题名,不要截断标题\n"
                + "2. learningQuestions 必须贴合原文,不要泛泛而谈\n"
                + "3. impactTargets/keyTerms/learningQuestions 使用字符串数组\n"
                + "4. confidence 为 0 到 1\n"
                + "5. 深度解读字段要求有实质内容,不能简单重复标题或摘要\n"
                + "6. 对投资/创业的影响要有具体分析,而非泛泛而谈\n\n"
                + "【文章信息】\n"
                + "来源: " + safe(article.getSourceName()) + "\n"
                + "标题: " + safe(article.getTitle()) + "\n"
                + "URL: " + safe(article.getUrl()) + "\n"
                + "分类: " + safe(article.getCategory()) + "\n"
                + "摘要: " + safe(article.getSummary()) + "\n"
                + "正文:\n" + limit(safe(article.getBody()), 9000);
    }

    private String traceInput(Article article, String userPrompt) {
        return "model=" + modelName() + "\narticleId=" + article.getId() + "\ntitle="
                + safe(article.getTitle()) + "\n\n" + limit(userPrompt, 4000);
    }

    private String traceOutput(ArticleInterpretation interpretation) {
        try {
            return objectMapper.writeValueAsString(interpretation);
        } catch (Exception ex) {
            return "source=" + safe(interpretation.getSource()) + ", topicName=" + safe(interpretation.getTopicName());
        }
    }

    private String modelName() {
        return llmChatClient == null ? "disabled" : llmChatClient.modelName();
    }

    private void record(String status, String input, String output, String error, long start) {
        if (agentRunRepository == null) {
            return;
        }
        agentRunRepository.record(NODE_NAME, status, input, output, error, System.currentTimeMillis() - start);
    }

    private String text(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText("");
        return isBlank(value) ? fallback : value.trim();
    }

    private List<String> list(JsonNode node, List<String> fallback) {
        List<String> values = new ArrayList<String>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                add(values, item.asText());
            }
        } else if (node != null && node.isTextual()) {
            for (String item : node.asText().split("[\\n,，、]+")) {
                add(values, item);
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private List<String> splitTargets(String value) {
        List<String> values = new ArrayList<String>();
        if (isBlank(value)) {
            return values;
        }
        for (String item : value.split("[、,，\\n]+")) {
            add(values, item);
        }
        return values;
    }

    private void add(List<String> values, String item) {
        if (!isBlank(item) && !values.contains(item.trim())) {
            values.add(item.trim());
        }
    }

    private String extractJson(String raw) {
        String value = safe(raw).trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String contentType(Article article) {
        String text = join(" ", article.getTitle(), article.getSummary(), article.getBody(), article.getUrl()).toLowerCase();
        if (text.contains("arxiv") || text.contains("abstract:")) {
            return "RESEARCH_PAPER";
        }
        if (text.contains("x.com/") || text.contains("twitter.com/") || text.contains("@")) {
            return "SOCIAL_POST";
        }
        if (text.contains("cloudflare") || text.contains("workers") || text.contains("serverless")) {
            return "TECH_PRACTICE";
        }
        return "ARTICLE";
    }

    private String join(String separator, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!isBlank(value)) {
                if (builder.length() > 0) {
                    builder.append(separator);
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
