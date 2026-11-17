package com.finscope.domain.news.ai;

import lombok.Data;
import com.finscope.common.enums.news.NewsAiRelation;
import java.util.ArrayList;
import java.util.List;

@Data
public class NewsAiDecision {
    private String contentKey;
    private NewsAiAnalysis analysis;
    private String targetKey;
    private NewsAiRelation relation;
    private List<String> newFacts = new ArrayList<>();
    private String reason;
}
