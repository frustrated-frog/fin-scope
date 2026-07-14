package com.finscope.domain.knowledge;

import com.finscope.domain.topic.Topic;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeOverview {
    /**
     * 已接收任务数量。
     */
    private int acceptedTaskCount;
    /**
     * 建议任务数量。
     */
    private int suggestedTaskCount;
    /**
     * 到期复习数量。
     */
    private int dueReviewCount;
    /**
     * 活跃主题数量。
     */
    private int activeTopicCount;
    /**
     * 行动列表。
     */
    private List<KnowledgeAction> actions;
    /**
     * 活跃主题列表。
     */
    private List<Topic> activeTopics;
    /**
     * 最近知识条目列表。
     */
    private List<KnowledgeEntry> recentEntries;
}
