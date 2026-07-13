package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchReportSynthesisAgent {
    private static final int REPORT_LLM_TIMEOUT_MS = 20_000;
    private static final String[] REQUIRED_HEADINGS = {
            "## 核心结论", "## 执行摘要", "## 命题拆解", "## 关键证据",
            "## 反方证据与风险", "## 结论边界与后续验证", "## 来源"
    };
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+", Pattern.CASE_INSENSITIVE);

    @Resource
    private LlmChatClient llmChatClient;

    public GeneratedResearchReport refine(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                                           GeneratedResearchReport fallback) {
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            return fallback;
        }
        if (!EvidenceSufficiency.assess(evidence).isSufficient()) {
            return fallback;
        }
        try {
            String raw = llmChatClient.complete(systemPrompt(), userPrompt(thesis, evidence, fallback),
                    REPORT_LLM_TIMEOUT_MS);
            String markdown = stripFence(raw);
            if (!isValid(markdown, evidence)) {
                return fallback;
            }
            String summary = section(markdown, "## 执行摘要", "## 命题拆解");
            return new GeneratedResearchReport(fallback.getTitle(), fallback.getConclusion(),
                    fallback.getConclusionDirection(), fallback.getConfidence(), summary, markdown, "LLM_VALIDATED");
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String systemPrompt() {
        return "你是 FinScope 命题研究报告 Agent。只能使用输入证据，不得补造事实、数字、来源或链接。"
                + "必须直接给出带条件的阶段性结论，不能用‘无法得出结论’结束。全文使用中文，英文标题应在正文中解释其中文含义。"
                + "输出纯 Markdown；执行摘要 800-1200 字，正文总长度 4000-8000 字，全文绝对不得超过 12000 字。"
                + "必须依次使用这些二级标题：核心结论、执行摘要、命题拆解、关键证据、反方证据与风险、机制推演、结论边界与后续验证、来源。"
                + "来源章节只能复用输入 URL。避免重复、标题堆砌和投资买卖指令。";
    }

    private String userPrompt(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                              GeneratedResearchReport fallback) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("研究问题：").append(value(thesis.getQuestion())).append("\n")
                .append("研究对象：").append(value(thesis.getSubjectName())).append("\n")
                .append("基线结论：").append(fallback.getConclusion()).append("\n")
                .append("置信度：").append(fallback.getConfidence()).append("\n\n证据卡：\n");
        int index = 1;
        for (ResearchEvidenceCard card : evidence) {
            Article article = card.getArticle();
            prompt.append(index++).append(". stance=").append(card.getStance())
                    .append("; score=").append(card.getRelevanceScore())
                    .append("; source=").append(value(article.getSourceName()))
                    .append("; title=").append(compact(article.getTitle(), 180))
                    .append("; claim=").append(compact(card.getClaim(), 280))
                    .append("; articleId=").append(article.getId())
                    .append("; url=").append(promptUrl(article.getUrl())).append("\n");
        }
        if (evidence.isEmpty()) {
            prompt.append("（没有达到相关性门槛的证据，请输出低置信度基线报告并明确验证清单。）\n");
        }
        return prompt.toString();
    }

    private boolean isValid(String markdown, List<ResearchEvidenceCard> evidence) {
        if (markdown == null || markdown.length() < 500 || markdown.length() > ResearchReportPolicy.MAX_REPORT_CHARACTERS
                || markdown.contains("无法得出结论")) {
            return false;
        }
        int last = -1;
        for (String heading : REQUIRED_HEADINGS) {
            int current = markdown.indexOf(heading);
            if (current <= last) return false;
            last = current;
        }
        String summary = section(markdown, "## 执行摘要", "## 命题拆解");
        if (summary.length() < 800 || summary.length() > ResearchReportPolicy.MAX_EXECUTIVE_SUMMARY_CHARACTERS) {
            return false;
        }
        Set<String> allowedUrls = new HashSet<String>();
        for (ResearchEvidenceCard card : evidence) {
            if (card.getArticle().getUrl() != null) allowedUrls.add(card.getArticle().getUrl());
        }
        Matcher matcher = URL_PATTERN.matcher(markdown);
        while (matcher.find()) {
            if (!allowedUrls.contains(matcher.group())) return false;
        }
        return true;
    }

    private String section(String markdown, String startHeading, String endHeading) {
        int start = markdown.indexOf(startHeading);
        int end = markdown.indexOf(endHeading, start + startHeading.length());
        if (start < 0 || end < 0) return "";
        return markdown.substring(start + startHeading.length(), end).trim();
    }

    private String stripFence(String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.startsWith("```markdown")) result = result.substring(11);
        else if (result.startsWith("```")) result = result.substring(3);
        if (result.endsWith("```")) result = result.substring(0, result.length() - 3);
        return result.trim();
    }

    private String compact(String value, int max) {
        String compacted = value(value).replaceAll("\\s+", " ");
        return compacted.length() <= max ? compacted : compacted.substring(0, max) + "…";
    }

    private String promptUrl(String url) {
        return url == null || url.length() > 500 ? "（长链接省略，请用文章 ID 溯源）" : url;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
