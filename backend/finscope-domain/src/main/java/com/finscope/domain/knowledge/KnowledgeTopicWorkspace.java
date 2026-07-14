package com.finscope.domain.knowledge;

import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import lombok.Data;

import java.util.List;

/** Bounded topic projection ordered from source facts to current judgment. */
@Data
public class KnowledgeTopicWorkspace {
    /**
     * 主题对象。
     */
    private Topic topic;
    /**
     * 复习状态。
     */
    private TopicReviewState reviewState;
    /**
     * 事件列表。
     */
    private List<EventCluster> events;
    /**
     * 证据列表。
     */
    private List<EvidenceItem> evidence;
    /**
     * 任务列表。
     */
    private List<LearningTask> tasks;
    /**
     * 条目列表。
     */
    private List<KnowledgeEntry> entries;
}
