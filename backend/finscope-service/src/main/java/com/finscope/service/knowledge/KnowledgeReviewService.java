package com.finscope.service.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.dao.knowledge.TopicEventRepository;
import com.finscope.dao.knowledge.TopicReviewStateRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeEnums;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.knowledge.KnowledgeReviewResult;
import com.finscope.domain.knowledge.TopicReviewState;
import com.finscope.domain.research.EvidenceItem;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Records a human review as an immutable knowledge entry plus a new schedule. */
@Service
public class KnowledgeReviewService {
    private static final Set<Integer> ALLOWED_INTERVALS = Collections.unmodifiableSet(
            new HashSet<Integer>(Arrays.asList(7, 14, 30, 90)));

    private final TopicRepository topics;
    private final TopicReviewStateRepository reviewStates;
    private final KnowledgeEntryRepository entries;
    private final EvidenceItemRepository evidence;
    private final TopicEventRepository topicEvents;
    private final KnowledgeProjectionJobRepository projectionJobs;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public KnowledgeReviewService(TopicRepository topics,
                                  TopicReviewStateRepository reviewStates,
                                  KnowledgeEntryRepository entries,
                                  EvidenceItemRepository evidence,
                                  TopicEventRepository topicEvents,
                                  KnowledgeProjectionJobRepository projectionJobs,
                                  ApplicationEventPublisher events) {
        this(topics, reviewStates, entries, evidence, topicEvents,
                projectionJobs, events, Clock.systemDefaultZone());
    }

    KnowledgeReviewService(TopicRepository topics,
                           TopicReviewStateRepository reviewStates,
                           KnowledgeEntryRepository entries,
                           EvidenceItemRepository evidence,
                           TopicEventRepository topicEvents,
                           KnowledgeProjectionJobRepository projectionJobs,
                           ApplicationEventPublisher events,
                           Clock clock) {
        this.topics = topics;
        this.reviewStates = reviewStates;
        this.entries = entries;
        this.evidence = evidence;
        this.topicEvents = topicEvents;
        this.projectionJobs = projectionJobs;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public KnowledgeReviewResult review(long topicId, String conclusion,
                                        String confidence, List<Long> evidenceIds,
                                        int intervalDays, long expectedRevision) {
        if (!ALLOWED_INTERVALS.contains(intervalDays)) {
            throw badRequest("复习间隔只能是 7、14、30 或 90 天");
        }
        if (isBlank(conclusion)) {
            throw badRequest("复习结论不能为空");
        }
        String parsedConfidence = parseConfidence(confidence);
        topics.findById(topicId).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
        TopicReviewState state = reviewStates.findByTopicId(topicId)
                .orElseGet(() -> reviewStates.createIfAbsent(topicId));
        if (state.getRevision() != expectedRevision) {
            throw conflict();
        }
        List<Long> selectedEvidence = evidenceIds == null
                ? Collections.<Long>emptyList() : evidenceIds;
        validateEvidence(topicId, selectedEvidence);

        KnowledgeEntry review = new KnowledgeEntry();
        review.setTopicId(topicId);
        review.setEntryType("REVIEW");
        review.setQuestionSnapshot("主题复习");
        review.setContentMarkdown(conclusion.trim());
        review.setConfidence(parsedConfidence);
        KnowledgeEntry draft = entries.saveDraft(review);
        entries.linkEvidence(draft.getId(), selectedEvidence);
        if (!entries.finalizeDraft(draft.getId(), draft.getRevision())) {
            throw conflict();
        }

        LocalDateTime reviewedAt = LocalDateTime.now(clock);
        LocalDateTime nextReviewAt = reviewedAt.plusDays(intervalDays);
        if (!reviewStates.recordReview(topicId, reviewedAt, nextReviewAt,
                intervalDays, expectedRevision)) {
            throw conflict();
        }
        KnowledgeEntry completed = entries.findById(draft.getId()).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "复习记录不存在"));
        KnowledgeProjectionJob job = projectionJobs.enqueue(topicId, completed.getId());
        events.publishEvent(new KnowledgeProjectionRequested(
                job.getId(), topicId, completed.getId()));

        KnowledgeReviewResult result = new KnowledgeReviewResult();
        result.setEntry(completed);
        result.setReviewedAt(reviewedAt);
        result.setNextReviewAt(nextReviewAt);
        result.setIntervalDays(intervalDays);
        result.setReviewCount(state.getReviewCount() + 1);
        result.setRevision(expectedRevision + 1);
        return result;
    }

    private void validateEvidence(long topicId, List<Long> evidenceIds) {
        for (Long evidenceId : evidenceIds) {
            EvidenceItem item = evidence.findById(evidenceId)
                    .orElseThrow(() -> badRequest("所选证据不存在: " + evidenceId));
            if (item.getEventId() == null || !topicEvents.isLinked(topicId, item.getEventId())) {
                throw badRequest("所选证据不属于当前主题");
            }
        }
    }

    private String parseConfidence(String confidence) {
        try {
            return KnowledgeEnums.Confidence.parse(confidence).name();
        } catch (IllegalArgumentException error) {
            throw badRequest("置信度必须是 LOW、MEDIUM 或 HIGH");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "复习记录已更新，请刷新后重试");
    }
}
