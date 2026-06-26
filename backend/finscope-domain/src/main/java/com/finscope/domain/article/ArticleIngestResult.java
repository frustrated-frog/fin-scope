package com.finscope.domain.article;

import com.finscope.domain.insight.InsightCard;
import lombok.Data;

@Data
public class ArticleIngestResult {
    private final Article article;
    private final InsightCard insightCard;
}
