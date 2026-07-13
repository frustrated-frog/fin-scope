package com.finscope.service.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.dao.knowledge.TopicEventRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeEnums;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Transactional command service for the learning-to-knowledge lifecycle.
 *
 * <p>All public mutations are semantic commands. No caller can directly set an
 * arbitrary task state or mark a task complete without a durable final entry.</p>
 */
@Service
public class KnowledgeLearningService {
    private final LearningTaskRepository tasks;
    private final TopicRepository topics;
    private final KnowledgeEntryRepository entries;
    private final EvidenceItemRepository evidence;
    private final TopicEventRepository topicEvents;
    private final KnowledgeProjectionJobRepository projectionJobs;
    private final LearningTaskPolicy policy;
    private final ApplicationEventPublisher events;

    public KnowledgeLearningService(LearningTaskRepository tasks,
                                    TopicRepository topics,
                                    KnowledgeEntryRepository entries,
                                    EvidenceItemRepository evidence,
                                    TopicEventRepository topicEvents,
                                    KnowledgeProjectionJobRepository projectionJobs,
                                    LearningTaskPolicy policy,
                                    ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.topics = topics;
        this.entries = entries;
        this.evidence = evidence;
        this.topicEvents = topicEvents;
        this.projectionJobs = projectionJobs;
        this.policy = policy;
        this.events = events;
    }

    @Transactional
    public LearningTask acceptSuggestion(long taskId, long topicId, long expectedRevision) {
        if (topicId <= 0) {
            throw badRequest("接受建议前必须选择主题");
        }
        Topic topic = requireTopic(topicId);
        if ("ARCHIVED".equals(topic.getLifecycleStatus())) {
            throw badRequest("已归档主题不能接收新的学习任务");
        }
        LearningTask task = requireTask(taskId);
        requireRevision(task, expectedRevision);
        requireTransition(task.getStatus(), "TODO");
        LocalDateTime acceptedAt = LocalDateTime.now();
        if (!tasks.transition(taskId, task.getStatus(), "TODO", topicId,
                acceptedAt, null, null, expectedRevision)) {
            throw conflict();
        }
        if (task.getEventId() != null) {
            topicEvents.link(topicId, task.getEventId(), "LEARNING_SOURCE");
        }
        return requireTask(taskId);
    }

    @Transactional
    public LearningTask startTask(long taskId, long expectedRevision) {
        LearningTask task = requireTask(taskId);
        requireRevision(task, expectedRevision);
        requireTransition(task.getStatus(), "IN_PROGRESS");
        if (task.getTopicId() == null) {
            throw badRequest("开始学习前必须将任务归入主题");
        }
        if (!tasks.transition(taskId, task.getStatus(), "IN_PROGRESS", task.getTopicId(),
                task.getAcceptedAt(), null, null, expectedRevision)) {
            throw conflict();
        }
        return requireTask(taskId);
    }

    @Transactional
    public KnowledgeEntry saveDraft(long taskId, long topicId, String markdown,
                                    String confidence, List<Long> evidenceIds,
                                    long expectedRevision) {
        LearningTask task = requireEditableTask(taskId, topicId);
        requireContent(markdown);
        String parsedConfidence = parseConfidence(confidence);
        List<Long> selectedEvidence = safeEvidenceIds(evidenceIds);
        validateEvidence(task, topicId, selectedEvidence);

        Optional<KnowledgeEntry> existing = entries.findDraftByTaskId(taskId);
        KnowledgeEntry draft;
        if (existing.isPresent()) {
            draft = existing.get();
            if (!entries.updateDraft(draft.getId(), markdown, parsedConfidence, expectedRevision)) {
                throw conflict();
            }
            draft = entries.findById(draft.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识草稿不存在"));
        } else {
            requireRevision(task, expectedRevision);
            draft = entries.saveDraft(newDraft(task, topicId, markdown, parsedConfidence));
        }
        entries.linkEvidence(draft.getId(), selectedEvidence);
        return draft;
    }

    @Transactional
    public KnowledgeEntry completeTask(long taskId, long topicId, String markdown,
                                       String confidence, List<Long> evidenceIds,
                                       long expectedRevision) {
        LearningTask task = requireEditableTask(taskId, topicId);
        requireRevision(task, expectedRevision);
        requireContent(markdown);
        requireTransition(task.getStatus(), "DONE");
        String parsedConfidence = parseConfidence(confidence);
        List<Long> selectedEvidence = safeEvidenceIds(evidenceIds);
        validateEvidence(task, topicId, selectedEvidence);

        KnowledgeEntry draft = prepareCompletionDraft(task, topicId, markdown, parsedConfidence);
        entries.linkEvidence(draft.getId(), selectedEvidence);
        try {
            if (!entries.finalizeDraft(draft.getId(), draft.getRevision())) {
                throw conflict();
            }
        } catch (DataAccessException error) {
            throw new BusinessException(ErrorCode.CONFLICT, "该任务已存在最终答案", error);
        }

        if (!tasks.transition(taskId, task.getStatus(), "DONE", topicId,
                task.getAcceptedAt(), null, "RECORDED", expectedRevision)) {
            throw conflict();
        }
        if (task.getEventId() != null) {
            topicEvents.link(topicId, task.getEventId(), "LEARNING_SOURCE");
        }

        KnowledgeEntry completed = entries.findById(draft.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识成果不存在"));
        KnowledgeProjectionJob job = projectionJobs.enqueue(topicId, completed.getId());
        events.publishEvent(new KnowledgeProjectionRequested(job.getId(), topicId, completed.getId()));
        return completed;
    }

    @Transactional
    public LearningTask dismissTask(long taskId, String reason, long expectedRevision) {
        LearningTask task = requireTask(taskId);
        requireRevision(task, expectedRevision);
        if ("IN_PROGRESS".equals(task.getStatus()) && isBlank(reason)) {
            throw badRequest("进行中的任务需要填写放弃原因");
        }
        requireTransition(task.getStatus(), "DISMISSED");
        if (!tasks.transition(taskId, task.getStatus(), "DISMISSED", task.getTopicId(),
                task.getAcceptedAt(), trimToNull(reason), null, expectedRevision)) {
            throw conflict();
        }
        return requireTask(taskId);
    }

    private KnowledgeEntry prepareCompletionDraft(LearningTask task, long topicId,
                                                   String markdown, String confidence) {
        Optional<KnowledgeEntry> existing = entries.findDraftByTaskId(task.getId());
        if (!existing.isPresent()) {
            return entries.saveDraft(newDraft(task, topicId, markdown, confidence));
        }
        KnowledgeEntry draft = existing.get();
        if (!entries.updateDraft(draft.getId(), markdown, confidence, draft.getRevision())) {
            throw conflict();
        }
        return entries.findById(draft.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识草稿不存在"));
    }

    private KnowledgeEntry newDraft(LearningTask task, long topicId,
                                    String markdown, String confidence) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setTopicId(topicId);
        entry.setLearningTaskId(task.getId());
        entry.setEntryType("ANSWER");
        entry.setQuestionSnapshot(task.getQuestion());
        entry.setContentMarkdown(markdown.trim());
        entry.setConfidence(confidence);
        return entry;
    }

    private void validateEvidence(LearningTask task, long topicId, List<Long> evidenceIds) {
        for (Long evidenceId : evidenceIds) {
            EvidenceItem item = evidence.findById(evidenceId)
                    .orElseThrow(() -> badRequest("所选证据不存在: " + evidenceId));
            boolean fromTaskEvent = task.getEventId() != null
                    && task.getEventId().equals(item.getEventId());
            boolean fromTopicEvent = item.getEventId() != null
                    && topicEvents.isLinked(topicId, item.getEventId());
            if (!fromTaskEvent && !fromTopicEvent) {
                throw badRequest("所选证据不属于当前任务或主题");
            }
        }
    }

    private LearningTask requireEditableTask(long taskId, long topicId) {
        LearningTask task = requireTask(taskId);
        if (!"IN_PROGRESS".equals(task.getStatus())) {
            throw badRequest("只有进行中的任务可以记录学习成果");
        }
        if (task.getTopicId() == null || task.getTopicId() != topicId) {
            throw badRequest("任务与主题不匹配");
        }
        requireTopic(topicId);
        return task;
    }

    private LearningTask requireTask(long taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学习任务不存在"));
    }

    private Topic requireTopic(long topicId) {
        return topics.findById(topicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
    }

    private void requireRevision(LearningTask task, long expectedRevision) {
        if (task.getRevision() != expectedRevision) {
            throw conflict();
        }
    }

    private void requireTransition(String from, String to) {
        if (!policy.canTransition(from, to)) {
            throw badRequest("不允许从 " + from + " 迁移到 " + to);
        }
    }

    private void requireContent(String markdown) {
        if (isBlank(markdown)) {
            throw badRequest("学习成果不能为空");
        }
    }

    private List<Long> safeEvidenceIds(List<Long> evidenceIds) {
        return evidenceIds == null ? Collections.emptyList() : evidenceIds;
    }

    private String parseConfidence(String confidence) {
        try {
            return KnowledgeEnums.Confidence.parse(confidence).name();
        } catch (IllegalArgumentException error) {
            throw badRequest("置信度必须是 LOW、MEDIUM 或 HIGH");
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "记录已更新，请刷新后重试");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
