package com.finscope.service.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.TopicEventRepository;
import com.finscope.dao.knowledge.TopicReviewStateRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeTopicWorkspace;
import com.finscope.domain.topic.Topic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Builds a bounded topic projection without article bodies or N+1 lookups. */
@Service
public class KnowledgeTopicService {
    private static final int EVENT_LIMIT = 20;
    private static final int EVIDENCE_LIMIT = 50;
    private static final int TASK_LIMIT = 50;
    private static final int ENTRY_LIMIT = 50;

    private final TopicRepository topics;
    private final TopicReviewStateRepository reviews;
    private final KnowledgeEntryRepository entries;
    private final LearningTaskRepository tasks;
    private final TopicEventRepository links;
    private final EventClusterRepository events;
    private final EvidenceItemRepository evidence;

    public KnowledgeTopicService(TopicRepository topics,
                                 TopicReviewStateRepository reviews,
                                 KnowledgeEntryRepository entries,
                                 LearningTaskRepository tasks,
                                 TopicEventRepository links,
                                 EventClusterRepository events,
                                 EvidenceItemRepository evidence) {
        this.topics = topics;
        this.reviews = reviews;
        this.entries = entries;
        this.tasks = tasks;
        this.links = links;
        this.events = events;
        this.evidence = evidence;
    }

    public KnowledgeTopicWorkspace load(long topicId) {
        Topic topic = topics.findById(topicId).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
        List<Long> allEventIds = links.findEventIds(topicId);
        List<Long> eventIds = new ArrayList<Long>(allEventIds.subList(
                0, Math.min(allEventIds.size(), EVENT_LIMIT)));
        KnowledgeTopicWorkspace workspace = new KnowledgeTopicWorkspace();
        workspace.setTopic(topic);
        workspace.setReviewState(reviews.findByTopicId(topicId).orElse(null));
        workspace.setEntries(entries.findFinalByTopicId(topicId, ENTRY_LIMIT, 0));
        workspace.setTasks(tasks.findPage(null, topicId, null, 0, TASK_LIMIT));
        workspace.setEvents(events.findByIds(eventIds, EVENT_LIMIT));
        workspace.setEvidence(evidence.findByEventIds(eventIds, EVIDENCE_LIMIT));
        return workspace;
    }

    @Transactional
    public Topic create(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "主题名称不能为空");
        }
        Topic topic = new Topic();
        topic.setName(name.trim());
        topic.setDescription(description == null ? null : description.trim());
        String baseSlug = name.trim().toLowerCase().replaceAll("\\s+", "-");
        topic.setSlug(topics.findBySlug(baseSlug).isPresent()
                ? baseSlug + "-" + Long.toString(System.currentTimeMillis(), 36)
                : baseSlug);
        topic.setLifecycleStatus("ACTIVE");
        topic.setMasteryStatus("EXPLORING");
        return topics.save(topic);
    }
}
