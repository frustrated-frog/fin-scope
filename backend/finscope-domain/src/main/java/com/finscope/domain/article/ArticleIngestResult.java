package com.finscope.domain.article;

import com.finscope.domain.insight.InsightCard;
import lombok.Data;

@Data
public class ArticleIngestResult {
    /**
     * 入库文章对象。
     */
    private final Article article;
    /**
     * 文章生成的情报卡片。
     */
    private final InsightCard insightCard;
}
