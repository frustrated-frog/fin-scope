package com.finscope.domain.article;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Article {

    private Long id;
    private Long sourceId;
    private String sourceName;
    private String title;
    private String url;
    private LocalDateTime publishedAt;
    private String summary;
    private String body;
    private String category;
    private String noveltyType;
    private String noveltyReason;
    private LocalDateTime fetchedAt;

    public static Article createFetched(Long sourceId, String sourceName, String title, String url,
                                        LocalDateTime publishedAt, String summary, String body) {
        Article article = new Article();
        article.sourceId = sourceId;
        article.sourceName = sourceName;
        article.title = title;
        article.url = url;
        article.publishedAt = publishedAt;
        article.summary = summary;
        article.body = body;
        article.category = "市场";
        article.noveltyType = "NEW";
        article.noveltyReason = "首次进入信息流";
        article.fetchedAt = LocalDateTime.now();
        return article;
    }
}
