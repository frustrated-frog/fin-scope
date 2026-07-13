package com.finscope.domain.knowledge;

import com.finscope.domain.topic.Topic;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeOverview {
    private int acceptedTaskCount;
    private int suggestedTaskCount;
    private int dueReviewCount;
    private int activeTopicCount;
    private List<KnowledgeAction> actions;
    private List<Topic> activeTopics;
    private List<KnowledgeEntry> recentEntries;
}
