package com.finscope.domain.news.ai;

import lombok.Data;
import com.finscope.common.enums.news.NewsAiRelation;

@Data
public class NewsAiBinding {
    private String contentKey;
    private String eventKey;
    private String contextHash;
    private NewsAiRelation relation;
    private NewsAiAnalysis analysis;
    private java.util.List<String> newFacts = new java.util.ArrayList<>();
}
