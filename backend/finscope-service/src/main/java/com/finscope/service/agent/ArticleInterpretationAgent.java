package com.finscope.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.insight.InsightSection;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.insight.InsightCardGenerator;
import com.finscope.service.research.ResearchRunContext;
import com.finscope.service.topic.TopicExtraction;
import com.finscope.service.topic.TopicExtractor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ArticleInterpretationAgent {
    private static final String NODE_NAME = "article-interpret";
    private static final int PROMPT_BODY_LIMIT = 9000;
    private static final int FULL_TEXT_MIN_LENGTH = 800;

    @Resource
    private LlmChatClient llmChatClient;
    @Resource
    private AgentRunRepository agentRunRepository;
    @Resource
    private TopicExtractor topicExtractor;
    @Resource
    private InsightCardGenerator insightCardGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();


    public boolean isConfigured() {
        return llmChatClient != null && llmChatClient.isConfigured();
    }

    public ArticleInterpretation interpret(Article article) {
        long start = System.currentTimeMillis();
        ArticleInterpretationInput input = ArticleInterpretationInput.from(article);
        String userPrompt = buildUserPrompt(input);
        if (!isConfigured()) {
            ArticleInterpretation fallback = fallback(article);
            record(input, "FALLBACK", traceInput(input, userPrompt), traceOutput(fallback), null, start);
            return fallback;
        }
        String raw = null;
        try {
            raw = llmChatClient.complete(systemPrompt(), userPrompt);
            ArticleInterpretation interpretation = parse(raw, input);
            record(input, "SUCCESS", traceInput(input, userPrompt), raw, null, start);
            return interpretation;
        } catch (InterpretationRejectedException ex) {
            ArticleInterpretation fallback = fallback(article);
            record(input, "REJECTED", traceInput(input, userPrompt), raw, ex.getMessage(), start);
            return fallback;
        } catch (Exception ex) {
            record(input, "FAILED", traceInput(input, userPrompt), raw, ex.getMessage(), start);
            return fallback(article);
        }
    }

    private ArticleInterpretation parse(String raw, ArticleInterpretationInput input) throws Exception {
        String json = extractJson(raw);
        JsonNode root = objectMapper.readTree(json);
        ArticleInterpretation fallback = fallback(input.article);
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
        interpretation.setAnalysisSections(sectionList(root.get("analysisSections")));

        validate(interpretation, fallback);
        validateAgainstEvidence(interpretation, input);
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

    private void validateAgainstEvidence(ArticleInterpretation interpretation, ArticleInterpretationInput input) {
        if (input.hasSubstantialText() && claimsBodyMissing(interpretation)) {
            throw new InterpretationRejectedException(
                    "LLM output contradicted article evidence: contentQuality=" + input.contentQuality
                            + ", bodyLength=" + input.bodyLength);
        }
    }

    private boolean claimsBodyMissing(ArticleInterpretation interpretation) {
        String text = join(" ",
                interpretation.getTopicName(),
                interpretation.getTopicDescription(),
                interpretation.getOneSentenceSummary(),
                interpretation.getCoreEvent(),
                interpretation.getImportance(),
                interpretation.getBackground(),
                interpretation.getKeyData(),
                interpretation.getTimeline(),
                interpretation.getRelatedParties(),
                interpretation.getRiskFactors(),
                interpretation.getFutureOutlook(),
                interpretation.getImpactOnInvestment(),
                interpretation.getImpactOnStartup(),
                interpretation.getProfessionalInsight(),
                interpretation.getFacts(),
                interpretation.getReasoning(),
                interpretation.getOpinions(),
                sectionText(interpretation.getAnalysisSections())).toLowerCase(Locale.ROOT);
        return containsAny(text,
                "正文未抓取",
                "正文未提供",
                "未提供文章正文",
                "未提供正文",
                "正文缺失",
                "缺少正文",
                "未获得文章主体内容",
                "未抓取到正文",
                "current data only shows",
                "no article body",
                "body is missing");
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
        interpretation.setAnalysisSections(new ArrayList<InsightSection>());
        interpretation.setConfidence(0.45);
        return interpretation;
    }

    private String systemPrompt() {
        return "你是 FinScope 的资深金融分析师和投研专家。你的任务是对抓取的文章进行深度解读,生成结构化的投研分析卡片。"
                + "分析需要按文章分类选择小标题,不要对所有文章套同一套模板。"
                + "同时要区分事实、推理和观点,提供专业的独立判断。"
                + "如果原文主要是英文,必须用中文输出,并在 analysisSections 第一项给出中文译文摘要。"
                + "只返回 JSON,不返回 Markdown,不要包裹代码块。";
    }

    private String buildUserPrompt(ArticleInterpretationInput input) {
        Article article = input.article;
        return "请深度解读下面文章,输出JSON对象。\n\n"
                + "【字段说明】\n"
                + "基础字段: contentType, topicName, topicDescription, oneSentenceSummary, coreEvent, importance, impactTargets, keyTerms, learningQuestions, confidence\n"
                + "动态解读字段: analysisSections, 数组, 每项包含 title 和 content。title 必须使用中文。\n"
                + "请按分类选择小标题:\n"
                + "- 金融: 发生了什么;关键数据/政策变量;影响链条;受影响资产;投资含义;反证与风险;下一观察窗口\n"
                + "- 市场: 政策/事件脉络;发布会/公告要点;市场反应;受影响方向;短期与中期影响;拥挤度与风险点;下一观察窗口\n"
                + "- 自我提升: 核心观点;适用场景;方法步骤;背后的原则;可以立刻做什么;常见误区/边界;给自己的复盘问题\n"
                + "- 前沿技术: 它做了什么;解决了什么问题;关键机制/技术路线;性能/成本/体验变化;生态与竞争格局;落地场景;风险与限制;我应该补什么知识\n"
                + "如果原文主要是英文,analysisSections 第一项必须是 title=中文译文摘要, content=自然中文摘要或短文完整译文。\n"
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
                + "4. analysisSections 使用对象数组,每项 title 不超过16字,content 必须是中文且基于原文证据\n"
                + "5. confidence 为 0 到 1\n"
                + "6. 深度解读字段要求有实质内容,不能简单重复标题或摘要\n"
                + "7. 对投资/创业的影响要有具体分析,而非泛泛而谈\n"
                + "8. 必须基于已提供正文证据进行判断,不要只依据 URL、作者名或互动数据臆测原文。\n"
                + "9. 如果 contentQuality 是 FULL_TEXT 或 PARTIAL_TEXT,禁止输出“正文未抓取”“未提供正文”“无法判断正文内容”等与输入事实冲突的说法。\n"
                + "10. 如果 bodyWasTruncated=true,只能说明基于已提供正文片段分析,不能说正文不可读。\n\n"
                + "【证据状态】\n"
                + "contentQuality: " + input.contentQuality + "\n"
                + "bodyLength: " + input.bodyLength + "\n"
                + "bodyWasTruncated: " + input.bodyWasTruncated + "\n"
                + "visibleBodyPreview: " + input.visibleBodyPreview + "\n\n"
                + "【文章信息】\n"
                + "来源: " + safe(article.getSourceName()) + "\n"
                + "标题: " + safe(article.getTitle()) + "\n"
                + "URL: " + safe(article.getUrl()) + "\n"
                + "分类: " + safe(article.getCategory()) + "\n"
                + "摘要: " + safe(article.getSummary()) + "\n"
                + "正文:\n" + input.promptBody;
    }

    private String traceInput(ArticleInterpretationInput input, String userPrompt) {
        return "model=" + modelName()
                + "\narticleId=" + input.article.getId()
                + "\ntitle=" + safe(input.article.getTitle())
                + "\ncontentQuality=" + input.contentQuality
                + "\nbodyLength=" + input.bodyLength
                + "\nbodyWasTruncated=" + input.bodyWasTruncated
                + "\n\n" + limit(userPrompt, 4000);
    }

    private String traceOutput(ArticleInterpretation interpretation) {
        try {
            return objectMapper.writeValueAsString(interpretation);
        } catch (Exception ex) {
            return "source=" + safe(interpretation.getSource()) + ", topicName=" + safe(interpretation.getTopicName());
        }
    }

    private List<InsightSection> sectionList(JsonNode node) {
        List<InsightSection> sections = new ArrayList<InsightSection>();
        if (node == null || !node.isArray()) {
            return sections;
        }
        for (JsonNode item : node) {
            String title = text(item, "title", "");
            String content = text(item, "content", "");
            if (!isBlank(title) && !isBlank(content)) {
                sections.add(new InsightSection(title.trim(), content.trim()));
            }
        }
        return sections;
    }

    private String sectionText(List<InsightSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<String>();
        for (InsightSection section : sections) {
            values.add(section.getTitle());
            values.add(section.getContent());
        }
        return join(" ", values.toArray(new String[0]));
    }

    private String modelName() {
        return llmChatClient == null ? "disabled" : llmChatClient.modelName();
    }

    private void record(ArticleInterpretationInput interpretationInput,
                        String status,
                        String input,
                        String output,
                        String error,
                        long start) {
        if (agentRunRepository == null) {
            return;
        }
        Long articleId = interpretationInput == null || interpretationInput.article == null
                ? null : interpretationInput.article.getId();
        agentRunRepository.record(ResearchRunContext.currentRunId(), null, articleId, NODE_NAME, status,
                input, output, error, System.currentTimeMillis() - start);
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

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (!isBlank(needle) && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    private static class ArticleInterpretationInput {
        private final Article article;
        private final String promptBody;
        private final String visibleBodyPreview;
        private final String contentQuality;
        private final int bodyLength;
        private final boolean bodyWasTruncated;

        private ArticleInterpretationInput(Article article,
                                           String promptBody,
                                           String visibleBodyPreview,
                                           String contentQuality,
                                           int bodyLength,
                                           boolean bodyWasTruncated) {
            this.article = article;
            this.promptBody = promptBody;
            this.visibleBodyPreview = visibleBodyPreview;
            this.contentQuality = contentQuality;
            this.bodyLength = bodyLength;
            this.bodyWasTruncated = bodyWasTruncated;
        }

        private static ArticleInterpretationInput from(Article article) {
            String body = safeText(article == null ? null : article.getBody());
            String content = extractVisibleContent(body);
            String quality = quality(body, content);
            String promptBody = limitText(body, PROMPT_BODY_LIMIT);
            return new ArticleInterpretationInput(
                    article,
                    promptBody,
                    limitText(content, 240),
                    quality,
                    body.length(),
                    body.length() > PROMPT_BODY_LIMIT);
        }

        private boolean hasSubstantialText() {
            return ("FULL_TEXT".equals(contentQuality) || "PARTIAL_TEXT".equals(contentQuality))
                    && visibleBodyPreview.length() >= 40;
        }

        private static String quality(String body, String content) {
            if (isBlankText(body)) {
                return "EMPTY";
            }
            if (looksLikeLinkOnly(content)) {
                return "LINK_ONLY";
            }
            if (body.length() >= FULL_TEXT_MIN_LENGTH) {
                return "FULL_TEXT";
            }
            return "PARTIAL_TEXT";
        }

        private static String extractVisibleContent(String body) {
            if (isBlankText(body)) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            boolean contentStarted = false;
            for (String rawLine : body.split("\\R+")) {
                String line = rawLine.trim();
                if (isBlankText(line)) {
                    continue;
                }
                if (line.startsWith("作者：") || line.startsWith("发布时间：") || line.startsWith("互动：")) {
                    continue;
                }
                if ("正文：".equals(line)) {
                    contentStarted = true;
                    continue;
                }
                if (contentStarted || !line.contains("：")) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(line);
                }
            }
            return builder.length() == 0 ? body.trim() : builder.toString().trim();
        }

        private static boolean looksLikeLinkOnly(String content) {
            String value = safeText(content);
            if (value.isEmpty()) {
                return false;
            }
            String withoutUrls = value
                    .replaceAll("https?://\\S+", "")
                    .replaceAll("(?i)\\b(?:x|twitter)\\.com/\\S+", "")
                    .trim();
            boolean containsArticleLink = value.toLowerCase(Locale.ROOT).contains("x.com/i/article/")
                    || value.toLowerCase(Locale.ROOT).contains("twitter.com/i/article/");
            return containsArticleLink && withoutUrls.length() <= 8;
        }

        private static String limitText(String value, int maxLength) {
            String text = safeText(value);
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength);
        }

        private static String safeText(String value) {
            return value == null ? "" : value.trim();
        }

        private static boolean isBlankText(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    private static class InterpretationRejectedException extends RuntimeException {
        private InterpretationRejectedException(String message) {
            super(message);
        }
    }
}
