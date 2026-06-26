package com.finscope.service.article;

import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleCardView {
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
    private InsightCard insightCard;

    public ArticleCardView(Article article, InsightCard insightCard) {
        this.id = article.getId();
        this.sourceId = article.getSourceId();
        this.sourceName = article.getSourceName();
        this.title = article.getTitle();
        this.url = article.getUrl();
        this.publishedAt = article.getPublishedAt();
        this.summary = article.getSummary();
        this.body = article.getBody();
        this.category = article.getCategory();
        this.noveltyType = article.getNoveltyType();
        this.noveltyReason = article.getNoveltyReason();
        this.fetchedAt = article.getFetchedAt();
        this.insightCard = insightCard;
    }
}
