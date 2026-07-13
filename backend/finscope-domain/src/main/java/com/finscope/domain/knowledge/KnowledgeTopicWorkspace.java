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
    private Topic topic;
    private TopicReviewState reviewState;
    private List<EventCluster> events;
    private List<EvidenceItem> evidence;
    private List<LearningTask> tasks;
    private List<KnowledgeEntry> entries;
}
