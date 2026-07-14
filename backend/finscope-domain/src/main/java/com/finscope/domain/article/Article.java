package com.finscope.domain.article;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Article {

    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 信息源 ID。
     */
    private Long sourceId;
    /**
     * 信息源名称。
     */
    private String sourceName;
    /**
     * 标题。
     */
    private String title;
    /**
     * 资源 URL。
     */
    private String url;
    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 正文内容。
     */
    private String body;
    /**
     * 内容分类。
     */
    private String category;
    /**
     * 新意类型。
     */
    private String noveltyType;
    /**
     * 新意判断原因。
     */
    private String noveltyReason;
    /**
     * 抓取时间。
     */
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
