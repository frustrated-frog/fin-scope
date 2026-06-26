package com.finscope.service.brief;

import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BriefGenerator {
    private static final List<String> SECTIONS = Arrays.asList("宏观", "市场", "行业", "公司", "政策");

    public String generate(LocalDate date, List<Article> articles) {
        return generate(date, articles, Collections.emptyMap());
    }

    public String generate(LocalDate date, List<Article> articles, Map<Long, InsightCard> cardsByArticleId) {
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
        markdown.append("# FinScope Daily Brief - ").append(date).append("\n\n");
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
