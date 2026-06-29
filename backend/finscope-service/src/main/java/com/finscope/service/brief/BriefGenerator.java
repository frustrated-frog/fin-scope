package com.finscope.service.brief;

import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.research.BriefResearchContext;
import com.finscope.domain.research.ContentIdea;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.research.ResearchEnums;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BriefGenerator {
    private static final String BRIEF_TITLE_PREFIX = "每日金融、投资、创业学习简报 - ";
    private static final String BRIEF_POSITIONING = "帮助建立长期判断力，不提供具体买卖建议。";
    private static final List<String> SECTIONS = Arrays.asList("宏观", "市场", "行业", "公司", "政策");
    private static final DateTimeFormatter GENERATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String generate(LocalDate date, List<Article> articles) {
        return generate(date, articles, Collections.emptyMap());
    }

    public String generate(LocalDate date, List<Article> articles, Map<Long, InsightCard> cardsByArticleId) {
        return generateArticleBrief(date, articles, cardsByArticleId);
    }

    public String generate(LocalDate date, List<Article> articles, BriefResearchContext context) {
        if (context != null && !context.isEmpty()) {
            return generateResearchBrief(date, context);
        }
        return generateArticleBrief(date, articles, Collections.<Long, InsightCard>emptyMap());
    }

    private String generateArticleBrief(LocalDate date, List<Article> articles, Map<Long, InsightCard> cardsByArticleId) {
        Map<String, StringBuilder> sections = new LinkedHashMap<String, StringBuilder>();
        for (String section : SECTIONS) {
            sections.put(section, new StringBuilder());
        }

        for (Article article : articles) {
            if (!"NEW".equalsIgnoreCase(article.getNoveltyType())
                    && !"FOLLOW_UP".equalsIgnoreCase(article.getNoveltyType())) {
                continue;
            }
            String category = sections.containsKey(article.getCategory()) ? article.getCategory() : "市场";
            InsightCard card = cardsByArticleId == null ? null : cardsByArticleId.get(article.getId());
            if (card == null) {
                sections.get(category)
                        .append("- [").append(nullToEmpty(article.getTitle())).append("](")
                        .append(nullToEmpty(article.getUrl())).append(")")
                        .append(" - ").append(nullToEmpty(article.getSummary()))
                        .append(" 来源：").append(nullToEmpty(article.getSourceName()))
                        .append("\n");
            } else {
                sections.get(category)
                        .append(card.getCardMarkdown())
                        .append("\n");
            }
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(BRIEF_TITLE_PREFIX).append(date).append("\n\n");
        markdown.append("> 本简报用于个人学习与信息整理，不构成投资建议。\n\n");
        for (Map.Entry<String, StringBuilder> entry : sections.entrySet()) {
            markdown.append("## ").append(entry.getKey()).append("\n\n");
            if (entry.getValue().length() == 0) {
                markdown.append("- 今日暂无新的高价值内容。\n");
            } else {
                markdown.append(entry.getValue());
            }
            markdown.append("\n");
        }
        markdown.append("## 今日学习问题\n\n");
        markdown.append("- 今天哪些信息是旧事件的新增变量？\n");
        markdown.append("- 哪些概念需要补充到主题库？\n");
        markdown.append("- 哪些内容未来可以发展成自媒体选题？\n");
        return markdown.toString();
    }

    private String generateResearchBrief(LocalDate date, BriefResearchContext context) {
        List<EventCluster> featuredEvents = context.getEvents().stream()
                .filter(this::isFeaturedEvent)
                .collect(Collectors.toList());
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(BRIEF_TITLE_PREFIX).append(date).append("\n\n");
        markdown.append("生成时间：").append(LocalDateTime.now().format(GENERATED_AT_FORMATTER)).append("\n");
        markdown.append("定位：").append(BRIEF_POSITIONING).append("\n\n");
        markdown.append("> 本简报用于个人学习与信息整理，不构成投资建议。\n\n");

        markdown.append("## 今日新变量\n\n");
        if (featuredEvents.isEmpty()) {
            markdown.append("- 今日暂无结构化事件更新。\n\n");
        } else {
            for (EventCluster event : featuredEvents) {
                markdown.append("- **").append(nullToEmpty(event.getCanonicalTitle())).append("**：")
                        .append(nullToEmpty(event.getSummary())).append("（")
                        .append(nullToEmpty(event.getNoveltyState())).append("）\n");
            }
            markdown.append("\n");
        }

        markdown.append("## 事件追踪\n\n");
        appendEventSection(markdown, context.getEvents());

        markdown.append("## 中国宏观\n\n");
        appendEventSection(markdown, eventsByTheme(featuredEvents, ResearchEnums.THEME_CHINA_MACRO));

        markdown.append("## 公司与 IPO\n\n");
        appendEventSection(markdown, eventsByTheme(featuredEvents, ResearchEnums.THEME_COMPANY_IPO));

        markdown.append("## AI 创业\n\n");
        appendEventSection(markdown, eventsByTheme(featuredEvents, ResearchEnums.THEME_AI_STARTUP));

        markdown.append("## 今日证据来源\n\n");
        if (context.getEvidenceItems().isEmpty()) {
            markdown.append("- 今日暂无可展示证据。\n\n");
        } else {
            for (EvidenceItem item : context.getEvidenceItems()) {
                markdown.append("- [").append(nullToEmpty(item.getSourceTier())).append(" / ")
                        .append(nullToEmpty(item.getEvidenceType())).append("] ")
                        .append(nullToEmpty(item.getClaim())).append("（置信度 ")
                        .append(item.getConfidence() == null ? 0 : item.getConfidence()).append("）\n");
            }
            markdown.append("\n");
        }

        markdown.append("## 今天要补的金融知识\n\n");
        if (context.getLearningTasks().isEmpty()) {
            markdown.append("- 今日暂无新增学习任务。\n\n");
        } else {
            for (LearningTask task : context.getLearningTasks()) {
                markdown.append("- ").append(nullToEmpty(task.getQuestion()));
                if (task.getWhyNeeded() != null && !task.getWhyNeeded().trim().isEmpty()) {
                    markdown.append("：").append(task.getWhyNeeded().trim());
                }
                markdown.append("\n");
            }
            markdown.append("\n");
        }

        markdown.append("## 可发展为自媒体选题\n\n");
        if (context.getContentIdeas().isEmpty()) {
            markdown.append("- 今日暂无新增内容选题。\n\n");
        } else {
            for (ContentIdea idea : context.getContentIdeas()) {
                markdown.append("- **").append(nullToEmpty(idea.getTitle())).append("**（")
                        .append(idea.getScore() == null ? 0 : idea.getScore()).append("分）\n");
                markdown.append("  角度：").append(nullToEmpty(idea.getAngle())).append("\n");
                markdown.append("  大纲：").append(nullToEmpty(idea.getOutline()).replace("\n", " / ")).append("\n");
            }
            markdown.append("\n");
        }

        markdown.append("## 今日思考题\n\n");
        markdown.append("- 今天哪些事件已经从资讯变成了可复用的分析框架？\n");
        markdown.append("- 哪条证据最能改变你对该主题的理解？\n");
        markdown.append("- 哪个选题最值得写成长期沉淀内容？\n");
        return markdown.toString();
    }

    private void appendEventSection(StringBuilder markdown, List<EventCluster> events) {
        if (events.isEmpty()) {
            markdown.append("- 今日暂无相关高价值事件。\n\n");
            return;
        }
        for (EventCluster event : events) {
            markdown.append("- **").append(nullToEmpty(event.getCanonicalTitle())).append("**");
            markdown.append("：").append(nullToEmpty(event.getSummary()));
            markdown.append("（").append(themeLabel(event.getThemeCode()));
            markdown.append(" / ").append(nullToEmpty(event.getNoveltyState()));
            markdown.append(" / 重要性 ").append(event.getImportanceScore() == null ? 0 : event.getImportanceScore());
            markdown.append(" / 证据 ").append(event.getEvidenceCount() == null ? 0 : event.getEvidenceCount());
            markdown.append("）\n");
        }
        markdown.append("\n");
    }

    private List<EventCluster> eventsByTheme(List<EventCluster> events, String themeCode) {
        return events.stream()
                .filter(event -> themeCode.equals(event.getThemeCode()))
                .collect(Collectors.toList());
    }

    private boolean isFeaturedEvent(EventCluster event) {
        if (event == null) {
            return false;
        }
        String noveltyState = nullToEmpty(event.getNoveltyState());
        boolean isMeaningfulNovelty = ResearchEnums.NOVELTY_NEW.equalsIgnoreCase(noveltyState)
                || ResearchEnums.NOVELTY_FOLLOW_UP.equalsIgnoreCase(noveltyState);
        int importance = event.getImportanceScore() == null ? 0 : event.getImportanceScore();
        int evidenceCount = event.getEvidenceCount() == null ? 0 : event.getEvidenceCount();
        return isMeaningfulNovelty && importance >= 60 && evidenceCount >= 1;
    }

    private String themeLabel(String themeCode) {
        if (ResearchEnums.THEME_CHINA_MACRO.equals(themeCode)) {
            return "中国宏观";
        }
        if (ResearchEnums.THEME_COMPANY_IPO.equals(themeCode)) {
            return "公司 / IPO";
        }
        if (ResearchEnums.THEME_AI_STARTUP.equals(themeCode)) {
            return "AI 创业";
        }
        if (ResearchEnums.THEME_MARKET.equals(themeCode)) {
            return "市场";
        }
        return "综合";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
