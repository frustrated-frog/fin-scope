package com.finscope.service.insight;

import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.service.agent.ArticleInterpretation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class InsightCardGenerator {
    public InsightCard generate(Article article) {
        InsightCard card = new InsightCard();
        card.setArticleId(article.getId());
        card.setTitle(empty(article.getTitle(), "未命名文章"));
        card.setSourceName(empty(article.getSourceName(), "未知来源"));
        card.setSourceUrl(article.getUrl());
        card.setPublishedAt(article.getPublishedAt());
        card.setNoveltyType(empty(article.getNoveltyType(), "NEW"));
        card.setNoveltyReason(empty(article.getNoveltyReason(), "首次进入信息流"));

        String contentKind = detectContentKind(article);
        String evidence = buildOneSentenceSummary(article, contentKind);
        card.setOneSentenceSummary(limit(evidence, 120));
        card.setCoreEvent(buildCoreEvent(article, contentKind, card.getOneSentenceSummary()));
        card.setImportance(buildImportance(article, contentKind));
        card.setImpactTargets(buildImpactTargets(article, contentKind));
        card.setFollowUpQuestions(buildFollowUpQuestions(article, contentKind));
        card.setCardMarkdown(renderMarkdown(card));
        return card;
    }

    public InsightCard generate(Article article, ArticleInterpretation interpretation) {
        if (interpretation == null) {
            return generate(article);
        }
        InsightCard card = new InsightCard();
        card.setArticleId(article.getId());
        card.setTitle(empty(article.getTitle(), "未命名文章"));
        card.setSourceName(empty(article.getSourceName(), "未知来源"));
        card.setSourceUrl(article.getUrl());
        card.setPublishedAt(article.getPublishedAt());
        card.setNoveltyType(empty(article.getNoveltyType(), "NEW"));
        card.setNoveltyReason(empty(article.getNoveltyReason(), "首次进入信息流"));

        // 基础字段
        card.setOneSentenceSummary(firstNonBlank(interpretation.getOneSentenceSummary(), empty(article.getSummary(), "")));
        card.setCoreEvent(firstNonBlank(interpretation.getCoreEvent(), card.getOneSentenceSummary()));
        card.setImportance(firstNonBlank(interpretation.getImportance(), "这条内容适合沉淀为个人学习和后续选题素材。"));
        card.setImpactTargets(joinTargets(interpretation.getImpactTargets()));
        card.setFollowUpQuestions(String.join("\n", interpretation.getLearningQuestions()));

        // 深度解读字段
        card.setBackground(empty(interpretation.getBackground(), ""));
        card.setKeyData(empty(interpretation.getKeyData(), ""));
        card.setTimeline(empty(interpretation.getTimeline(), ""));
        card.setRelatedParties(empty(interpretation.getRelatedParties(), ""));
        card.setRiskFactors(empty(interpretation.getRiskFactors(), ""));
        card.setFutureOutlook(empty(interpretation.getFutureOutlook(), ""));
        card.setImpactOnInvestment(empty(interpretation.getImpactOnInvestment(), ""));
        card.setImpactOnStartup(empty(interpretation.getImpactOnStartup(), ""));
        card.setProfessionalInsight(empty(interpretation.getProfessionalInsight(), ""));
        card.setFacts(empty(interpretation.getFacts(), ""));
        card.setReasoning(empty(interpretation.getReasoning(), ""));
        card.setOpinions(empty(interpretation.getOpinions(), ""));

        card.setCardMarkdown(renderMarkdown(card, interpretation));
        return card;
    }

    private String detectContentKind(Article article) {
        String text = searchable(article);
        String url = empty(article.getUrl(), "").toLowerCase(Locale.ROOT);
        if (url.contains("arxiv.org") || (text.contains("arxiv:") && text.contains("abstract:"))) {
            return "PAPER";
        }
        if (url.contains("x.com/") || url.contains("twitter.com/") || text.contains("互动：")
                || text.contains("likes=") || text.contains("@")) {
            return "SOCIAL";
        }
        return "GENERAL";
    }

    private String buildOneSentenceSummary(Article article, String contentKind) {
        if ("PAPER".equals(contentKind)) {
            String abstractText = firstNonBlank(extractAbstract(article), article.getSummary(), article.getBody());
            return "论文《" + empty(article.getTitle(), "未命名论文") + "》聚焦 "
                    + technicalTopic(article) + "：" + firstSentence(abstractText);
        }
        if ("SOCIAL".equals(contentKind)) {
            String content = firstNonBlank(firstContentParagraph(article.getBody()), article.getSummary(), article.getTitle());
            return "社媒长文《" + empty(article.getTitle(), "未命名内容") + "》：" + content;
        }
        String evidence = article.getSummary();
        if (isBlank(evidence)) {
            evidence = firstSentence(article.getBody());
        }
        if (isBlank(evidence)) {
            evidence = empty(article.getTitle(), "未命名文章");
        }
        return evidence;
    }

    private String buildCoreEvent(Article article, String contentKind, String summary) {
        if ("PAPER".equals(contentKind)) {
            StringBuilder event = new StringBuilder();
            event.append("arXiv/研究论文发布：《").append(empty(article.getTitle(), "未命名论文")).append("》");
            String authors = extractLine(article.getBody(), "作者：");
            String categories = extractLine(article.getBody(), "分类：");
            if (!isBlank(authors)) {
                event.append("；作者：").append(authors);
            }
            if (!isBlank(categories)) {
                event.append("；分类：").append(categories);
            }
            event.append("；核心贡献：").append(firstSentence(firstNonBlank(extractAbstract(article), article.getSummary())));
            return event.toString();
        }
        if ("SOCIAL".equals(contentKind)) {
            String author = extractLine(article.getBody(), "作者：");
            StringBuilder event = new StringBuilder();
            if (!isBlank(author)) {
                event.append(author).append(" 发布了");
            } else {
                event.append("有创作者发布了");
            }
            event.append("一篇社媒长文：《").append(empty(article.getTitle(), "未命名内容")).append("》");
            String firstParagraph = firstContentParagraph(article.getBody());
            if (!isBlank(firstParagraph)) {
                event.append("；核心信息：").append(firstParagraph);
            }
            return event.toString();
        }
        String title = empty(article.getTitle(), "这篇文章");
        if (!isBlank(summary) && !title.equals(summary)) {
            return title + "。关键信息：" + summary;
        }
        return title;
    }

    private String buildImportance(Article article, String contentKind) {
        if ("PAPER".equals(contentKind)) {
            String text = searchable(article);
            if (containsAny(text, "red-teaming", "attack vector", "vulnerab", "security", "安全", "adversarial")) {
                return "这篇论文直接面向 Agent 系统的安全评测问题：当 AI Agent 具备工具调用、规划和自主决策能力后，传统 LLM 单点测试不够，需要可复现的红队评测框架来定位攻击面。";
            }
            if (containsAny(text, "benchmark", "dataset", "evaluation", "基准", "评测")) {
                return "这类研究提供新的评测基准或实验框架，适合沉淀为技术路线卡片，用来跟踪模型能力、数据集和方法论的变化。";
            }
            return "这类论文代表一个研究方向的新问题定义、方法或实验结果，适合进入长期知识库而不是只作为一次性新闻消费。";
        }
        if ("SOCIAL".equals(contentKind)) {
            String text = searchable(article);
            if (isOsintContent(text)) {
                return "这条社媒长文把 OSINT 工具组合成公开信息检索和网络资产暴露面排查流程，价值在于沉淀工具边界、交叉验证方法和合法合规边界，而不是只记录传播热度。";
            }
            if (containsAny(text, "cloudflare", "workers", "serverless", "d1", "r2", "pages", "kv")) {
                return "这条内容把 Cloudflare、Workers、D1、R2 等开发者基础设施串成低成本实践样本，能转化为技术学习路线、工具选型和自媒体选题。";
            }
            return "社媒长文的价值在于捕捉真实创作者的实践经验和传播角度，可用于发现主题、验证用户关注点，并沉淀成后续知识卡片。";
        }
        String category = empty(article.getCategory(), "市场");
        String text = searchable(article);
        if (containsAny(text, "美联储", "fed", "降息", "加息", "利率", "cpi", "通胀")) {
            return "影响利率预期、资产定价和风险偏好，是宏观交易与组合再平衡的重要变量。";
        }
        if (containsAny(text, "监管", "政策", "国务院", "证监会", "央行")) {
            return "可能改变行业约束、资金流向和市场风险偏好，需要跟踪政策落地节奏。";
        }
        if (containsAny(text, "财报", "营收", "利润", "订单", "指引")) {
            return "直接影响公司基本面预期，适合作为估值、景气度和业绩兑现的观察样本。";
        }
        if ("行业".equals(category)) {
            return "反映行业景气度和供需变化，适合沉淀为长期主题观察。";
        }
        if ("公司".equals(category)) {
            return "可能改变单一公司预期，也可能映射到产业链上下游。";
        }
        return "可能影响短期市场情绪、资金偏好和后续选题价值。";
    }

    private String buildImpactTargets(Article article, String contentKind) {
        if ("PAPER".equals(contentKind)) {
            return joinTargets(technicalTargets(article));
        }
        if ("SOCIAL".equals(contentKind)) {
            return joinTargets(socialTargets(article));
        }
        String text = searchable(article);
        List<String> targets = new ArrayList<String>();
        addIf(targets, "黄金", containsAny(text, "黄金", "gold", "贵金属"));
        addIf(targets, "美元", containsAny(text, "美元", "dollar", "美元指数"));
        addIf(targets, "债券", containsAny(text, "债券", "国债", "收益率", "利率"));
        addIf(targets, "权益市场", containsAny(text, "股票", "股市", "权益", "指数", "etf"));
        addIf(targets, "科技成长", containsAny(text, "ai", "人工智能", "芯片", "半导体", "算力"));
        addIf(targets, "行业链条", containsAny(text, "行业", "产业链", "供需"));
        addIf(targets, "公司基本面", containsAny(text, "财报", "营收", "利润", "订单"));
        if (targets.isEmpty()) {
            targets.add(empty(article.getCategory(), "市场"));
            targets.add("个人学习主题");
        }
        return String.join("、", targets);
    }

    private String buildFollowUpQuestions(Article article, String contentKind) {
        if ("PAPER".equals(contentKind)) {
            List<String> questions = new ArrayList<String>();
            questions.add("论文的方法、数据或代码是否已经公开，能否复现核心实验？");
            questions.add("它相对已有 benchmark、框架或论文的新变量是什么？");
            questions.add("这个方向能否转化为自己的 Agent 项目模块、测试集或技术文章？");
            return String.join("\n", questions);
        }
        if ("SOCIAL".equals(contentKind)) {
            if (isOsintContent(searchable(article))) {
                List<String> questions = new ArrayList<String>();
                questions.add("这些 OSINT 工具的合法合规边界、数据来源和隐私风险分别是什么？");
                questions.add("哪些能力适合转化为防御侧资产排查流程，哪些高风险用法应该避免？");
                questions.add("不同工具之间如何交叉验证，避免把公开线索误判成确定事实？");
                return String.join("\n", questions);
            }
            List<String> questions = new ArrayList<String>();
            questions.add("文中提到的免费额度、限制条件和长期稳定性分别是什么？");
            questions.add("哪些工具可以真实复刻成自己的项目能力，哪些只是传播卖点？");
            questions.add("这条内容适合沉淀成教程、案例拆解还是选型对比？");
            return String.join("\n", questions);
        }
        String category = empty(article.getCategory(), "市场");
        List<String> questions = new ArrayList<String>();
        questions.add("下一次验证窗口是什么：数据、会议、公告还是财报？");
        questions.add("这条信息相对昨天的新变量是什么？");
        if ("宏观".equals(category)) {
            questions.add("利率、通胀、汇率和风险偏好里哪个变量最先反应？");
        } else if ("公司".equals(category)) {
            questions.add("这会影响收入、利润、估值还是市场叙事？");
        } else if ("政策".equals(category)) {
            questions.add("政策从表态到落地中间有哪些关键节点？");
        } else {
            questions.add("哪些资产、行业或主题最可能被二次传播？");
        }
        return String.join("\n", questions);
    }

    private String renderMarkdown(InsightCard card) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## 情报卡片：").append(card.getTitle()).append("\n\n");
        markdown.append("- 来源：").append(empty(card.getSourceName(), "未知来源")).append("\n");
        markdown.append("- 原始链接：").append(empty(card.getSourceUrl(), "")).append("\n");
        markdown.append("- 新意判断：").append(empty(card.getNoveltyType(), "NEW"))
                .append("，").append(empty(card.getNoveltyReason(), "")).append("\n\n");
        markdown.append("### 一句话摘要\n\n").append(empty(card.getOneSentenceSummary(), "")).append("\n\n");
        markdown.append("### 核心事件\n\n").append(empty(card.getCoreEvent(), "")).append("\n\n");
        markdown.append("### 为什么重要\n\n").append(empty(card.getImportance(), "")).append("\n\n");
        markdown.append("### 影响对象\n\n").append(empty(card.getImpactTargets(), "")).append("\n\n");
        markdown.append("### 后续观察\n\n");
        for (String question : splitLines(card.getFollowUpQuestions())) {
            markdown.append("- ").append(question).append("\n");
        }
        return markdown.toString();
    }

    private String renderMarkdown(InsightCard card, ArticleInterpretation interpretation) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## 情报卡片：").append(card.getTitle()).append("\n\n");

        // 基础信息
        markdown.append("- 来源：").append(empty(card.getSourceName(), "未知来源")).append("\n");
        markdown.append("- 原始链接：").append(empty(card.getSourceUrl(), "")).append("\n");
        markdown.append("- 新意判断：").append(empty(card.getNoveltyType(), "NEW"))
                .append("，").append(empty(card.getNoveltyReason(), "")).append("\n\n");

        // 核心内容
        markdown.append("### 一句话摘要\n\n").append(empty(card.getOneSentenceSummary(), "")).append("\n\n");
        markdown.append("### 核心事件\n\n").append(empty(card.getCoreEvent(), "")).append("\n\n");
        markdown.append("### 为什么重要\n\n").append(empty(card.getImportance(), "")).append("\n\n");
        markdown.append("### 影响对象\n\n").append(empty(card.getImpactTargets(), "")).append("\n\n");

        // 深度解读
        if (!isBlank(card.getBackground())) {
            markdown.append("### 背景是什么\n\n").append(card.getBackground()).append("\n\n");
        }
        if (!isBlank(card.getKeyData())) {
            markdown.append("### 关键数据\n\n").append(card.getKeyData()).append("\n\n");
        }
        if (!isBlank(card.getTimeline())) {
            markdown.append("### 时间线\n\n").append(card.getTimeline()).append("\n\n");
        }
        if (!isBlank(card.getRelatedParties())) {
            markdown.append("### 相关方\n\n").append(card.getRelatedParties()).append("\n\n");
        }
        if (!isBlank(card.getRiskFactors())) {
            markdown.append("### 风险因素\n\n").append(card.getRiskFactors()).append("\n\n");
        }
        if (!isBlank(card.getFutureOutlook())) {
            markdown.append("### 未来展望\n\n").append(card.getFutureOutlook()).append("\n\n");
        }
        if (!isBlank(card.getImpactOnInvestment())) {
            markdown.append("### 对投资的影响\n\n").append(card.getImpactOnInvestment()).append("\n\n");
        }
        if (!isBlank(card.getImpactOnStartup())) {
            markdown.append("### 对创业的影响\n\n").append(card.getImpactOnStartup()).append("\n\n");
        }
        if (!isBlank(card.getProfessionalInsight())) {
            markdown.append("### 专业解读\n\n").append(card.getProfessionalInsight()).append("\n\n");
        }

        // 事实、推理、观点
        boolean hasFRP = !isBlank(card.getFacts()) || !isBlank(card.getReasoning()) || !isBlank(card.getOpinions());
        if (hasFRP) {
            markdown.append("### 事实、推理与观点\n\n");
            if (!isBlank(card.getFacts())) {
                markdown.append("**事实**：").append(card.getFacts()).append("\n\n");
            }
            if (!isBlank(card.getReasoning())) {
                markdown.append("**推理**：").append(card.getReasoning()).append("\n\n");
            }
            if (!isBlank(card.getOpinions())) {
                markdown.append("**观点**：").append(card.getOpinions()).append("\n\n");
            }
        }

        // 后续观察
        markdown.append("### 后续观察\n\n");
        for (String question : splitLines(card.getFollowUpQuestions())) {
            markdown.append("- ").append(question).append("\n");
        }

        // 可沉淀主题
        if (interpretation != null && !isBlank(interpretation.getTopicName())) {
            markdown.append("\n### 可沉淀主题\n\n");
            markdown.append("- 主题：").append(interpretation.getTopicName()).append("\n");
            markdown.append("- 类型：").append(empty(interpretation.getContentType(), "ARTICLE")).append("\n");
            markdown.append("- 解读来源：").append(empty(interpretation.getSource(), "FALLBACK")).append("\n");
            if (!isBlank(interpretation.getTopicDescription())) {
                markdown.append("- 整理说明：").append(interpretation.getTopicDescription()).append("\n");
            }
            if (interpretation.getKeyTerms() != null && !interpretation.getKeyTerms().isEmpty()) {
                markdown.append("- 关键术语：").append(String.join("、", interpretation.getKeyTerms())).append("\n");
            }
        }

        return markdown.toString();
    }

    private List<String> splitLines(String value) {
        List<String> lines = new ArrayList<String>();
        if (isBlank(value)) {
            return lines;
        }
        for (String line : value.split("\\n")) {
            if (!isBlank(line)) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String firstSentence(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim();
        String[] sentences = normalized.split("(?<=[。！？.!?])");
        return sentences.length == 0 ? normalized : sentences[0].trim();
    }

    private String searchable(Article article) {
        return (empty(article.getTitle(), "") + " " + empty(article.getSummary(), "") + " "
                + empty(article.getBody(), "") + " " + empty(article.getCategory(), "")).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void addIf(List<String> targets, String target, boolean condition) {
        if (condition && !targets.contains(target)) {
            targets.add(target);
        }
    }

    private String technicalTopic(Article article) {
        String text = searchable(article);
        if (containsAny(text, "agent", "agentic") && containsAny(text, "security", "red-teaming", "attack", "安全")) {
            return "Agent 安全评测与动态红队";
        }
        if (containsAny(text, "benchmark", "evaluation", "基准", "评测")) {
            return "AI 评测基准与方法论";
        }
        if (containsAny(text, "reinforcement learning", "rl", "强化学习")) {
            return "强化学习与模型对齐";
        }
        return "AI 研究问题";
    }

    private List<String> technicalTargets(Article article) {
        String text = searchable(article);
        Set<String> targets = new LinkedHashSet<String>();
        String title = empty(article.getTitle(), "");
        int colon = title.indexOf(':');
        if (colon > 0) {
            targets.add(title.substring(0, colon).trim());
        }
        if (containsAny(text, "rift-bench")) {
            targets.add("RIFT-Bench");
        }
        if (containsAny(text, "agentic ai")) {
            targets.add("Agentic AI");
        }
        if (containsAny(text, "red-teaming", "red teaming")) {
            targets.add("动态红队");
        }
        if (containsAny(text, "llm", "large language model")) {
            targets.add("LLM");
        }
        String categories = extractLine(article.getBody(), "分类：");
        if (!isBlank(categories)) {
            for (String category : categories.split("[、, ]+")) {
                if (!isBlank(category)) {
                    targets.add(category.trim());
                }
            }
        }
        if (targets.isEmpty()) {
            targets.add("AI 研究主题");
        }
        return new ArrayList<String>(targets);
    }

    private List<String> socialTargets(Article article) {
        String text = searchable(article);
        Set<String> targets = new LinkedHashSet<String>();
        addIf(targets, "OSINT", isOsintContent(text));
        addIf(targets, "公开信息检索", containsAny(text, "公开信息", "开源情报"));
        addIf(targets, "网络资产搜索", containsAny(text, "网络资产", "查设备", "设备场景"));
        addIf(targets, "信息安全", isOsintContent(text));
        addIf(targets, "Cloudflare", containsAny(text, "cloudflare"));
        addIf(targets, "Workers", containsAny(text, "workers"));
        addIf(targets, "D1", containsAny(text, " d1 ", "做 d1", "d1 数据库"));
        addIf(targets, "R2", containsAny(text, " r2 ", "r2 存"));
        addIf(targets, "Pages", containsAny(text, "pages"));
        addIf(targets, "KV", containsAny(text, " kv ", "kv 做"));
        addIf(targets, "Serverless", containsAny(text, "serverless"));
        if (targets.isEmpty()) {
            targets.add("社媒实践经验");
        }
        return new ArrayList<String>(targets);
    }

    private boolean isOsintContent(String text) {
        return containsAny(text, "osint", "开源情报", "公开信息检索", "查人", "查公司", "查设备", "网络资产", "暴露面");
    }

    private void addIf(Set<String> targets, String target, boolean condition) {
        if (condition) {
            targets.add(target);
        }
    }

    private String joinTargets(List<String> targets) {
        return String.join("、", targets);
    }

    private String extractAbstract(Article article) {
        String text = firstNonBlank(article.getBody(), article.getSummary(), "");
        if (isBlank(text)) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int abstractIndex = lower.indexOf("abstract:");
        if (abstractIndex >= 0) {
            return text.substring(abstractIndex + "abstract:".length()).trim();
        }
        String chinesePrefix = "摘要：";
        int summaryIndex = text.indexOf(chinesePrefix);
        if (summaryIndex >= 0) {
            return text.substring(summaryIndex + chinesePrefix.length()).trim();
        }
        return text.trim();
    }

    private String firstContentParagraph(String body) {
        if (isBlank(body)) {
            return "";
        }
        boolean contentStarted = false;
        for (String rawLine : body.split("\\R+")) {
            String line = rawLine.trim();
            if (isBlank(line)) {
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
                return line;
            }
        }
        return "";
    }

    private String extractLine(String text, String prefix) {
        if (isBlank(text)) {
            return "";
        }
        for (String rawLine : text.split("\\R+")) {
            String line = rawLine.trim();
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private String firstNonBlank(String first, String second) {
        return firstNonBlank(first, second, "");
    }

    private String firstNonBlank(String first, String second, String third) {
        if (!isBlank(first)) {
            return first.trim();
        }
        if (!isBlank(second)) {
            return second.trim();
        }
        return isBlank(third) ? "" : third.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String empty(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
