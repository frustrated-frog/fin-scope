package com.finscope.service.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.dao.knowledge.TopicEventRepository;
import com.finscope.dao.knowledge.TopicReviewStateRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.knowledge.KnowledgeReviewResult;
import com.finscope.domain.knowledge.TopicReviewState;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeReviewServiceTest {
    private final TopicRepository topics = mock(TopicRepository.class);
    private final TopicReviewStateRepository reviewStates = mock(TopicReviewStateRepository.class);
    private final KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
    private final EvidenceItemRepository evidence = mock(EvidenceItemRepository.class);
    private final TopicEventRepository topicEvents = mock(TopicEventRepository.class);
    private final KnowledgeProjectionJobRepository projectionJobs = mock(KnowledgeProjectionJobRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC);
    private final KnowledgeReviewService service = new KnowledgeReviewService(
            topics, reviewStates, entries, evidence, topicEvents,
            projectionJobs, publisher, clock);

    @Test
    void recordsAReviewEntryAndSchedulesTheNextReviewAtomically() {
        Topic topic = new Topic();
        topic.setId(2L);
        topic.setName("Agent 工程化");
        TopicReviewState state = new TopicReviewState();
        state.setTopicId(2L);
        state.setRevision(3L);
        state.setReviewCount(4);
        EvidenceItem item = new EvidenceItem();
        item.setId(11L);
        item.setEventId(9L);
        KnowledgeEntry draft = new KnowledgeEntry();
        draft.setId(100L);
        draft.setRevision(0L);
        KnowledgeEntry completed = new KnowledgeEntry();
        completed.setId(100L);
        completed.setEntryStatus("FINAL");
        KnowledgeProjectionJob job = new KnowledgeProjectionJob();
        job.setId(200L);
        when(topics.findById(2L)).thenReturn(Optional.of(topic));
        when(reviewStates.findByTopicId(2L)).thenReturn(Optional.of(state));
        when(evidence.findById(11L)).thenReturn(Optional.of(item));
        when(topicEvents.isLinked(2L, 9L)).thenReturn(true);
        when(entries.saveDraft(any(KnowledgeEntry.class))).thenReturn(draft);
        when(entries.finalizeDraft(100L, 0L)).thenReturn(true);
        when(entries.findById(100L)).thenReturn(Optional.of(completed));
        when(reviewStates.recordReview(2L,
                LocalDateTime.of(2026, 7, 13, 6, 0),
                LocalDateTime.of(2026, 8, 12, 6, 0), 30, 3L)).thenReturn(true);
        when(projectionJobs.enqueue(2L, 100L)).thenReturn(job);

        KnowledgeReviewResult result = service.review(2L, "结论仍然成立，但需增加故障恢复指标。",
                "HIGH", Collections.singletonList(11L), 30, 3L);

        assertEquals(LocalDateTime.of(2026, 8, 12, 6, 0), result.getNextReviewAt());
        assertEquals(5, result.getReviewCount());
        verify(entries).linkEvidence(100L, Collections.singletonList(11L));
        verify(publisher).publishEvent(any(KnowledgeProjectionRequested.class));
    }

    @Test
    void rejectsUnsupportedIntervalsBlankConclusionsAndUnlinkedEvidence() {
        assertThrows(BusinessException.class, () -> service.review(
                2L, "结论", "MEDIUM", Collections.emptyList(), 8, 0L));
        assertThrows(BusinessException.class, () -> service.review(
                2L, " ", "MEDIUM", Collections.emptyList(), 7, 0L));

        Topic topic = new Topic();
        topic.setId(2L);
        TopicReviewState state = new TopicReviewState();
        state.setRevision(0L);
        EvidenceItem item = new EvidenceItem();
        item.setEventId(99L);
        when(topics.findById(2L)).thenReturn(Optional.of(topic));
        when(reviewStates.findByTopicId(2L)).thenReturn(Optional.of(state));
        when(evidence.findById(11L)).thenReturn(Optional.of(item));
        when(topicEvents.isLinked(2L, 99L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.review(
                2L, "结论", "MEDIUM", Collections.singletonList(11L), 7, 0L));
    }
}
