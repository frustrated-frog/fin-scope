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
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeLearningServiceTest {
    private LearningTaskRepository tasks;
    private TopicRepository topics;
    private KnowledgeEntryRepository entries;
    private EvidenceItemRepository evidence;
    private TopicEventRepository topicEvents;
    private KnowledgeProjectionJobRepository projectionJobs;
    private ApplicationEventPublisher events;
    private KnowledgeLearningService service;

    @BeforeEach
    void setUp() {
        tasks = mock(LearningTaskRepository.class);
        topics = mock(TopicRepository.class);
        entries = mock(KnowledgeEntryRepository.class);
        evidence = mock(EvidenceItemRepository.class);
        topicEvents = mock(TopicEventRepository.class);
        projectionJobs = mock(KnowledgeProjectionJobRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new KnowledgeLearningService(
                tasks, topics, entries, evidence, topicEvents, projectionJobs,
                new LearningTaskPolicy(), events
        );
        Topic topic = new Topic();
        topic.setId(2L);
        topic.setLifecycleStatus("ACTIVE");
        when(topics.findById(2L)).thenReturn(Optional.of(topic));
    }

    @Test
    void acceptSuggestionRequiresTopic() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.acceptSuggestion(1L, 0L, 0L));
        assertEquals(ErrorCode.REQUEST_PARAMETER_INVALID, error.getErrorCode());
    }

    @Test
    void startTaskRejectsStaleRevision() {
        LearningTask task = task("TODO", 3L);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(tasks.transition(eq(1L), eq("TODO"), eq("IN_PROGRESS"), eq(2L),
                any(), eq(null), eq(null), eq(3L))).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startTask(1L, 3L));
        assertEquals(ErrorCode.BUSINESS_CONFLICT, error.getErrorCode());
    }

    @Test
    void completeTaskRejectsBlankAnswer() {
        when(tasks.findById(1L)).thenReturn(Optional.of(task("IN_PROGRESS", 4L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeTask(1L, 2L, "  ", "MEDIUM", Collections.emptyList(), 4L, null));
        assertEquals(ErrorCode.REQUEST_PARAMETER_INVALID, error.getErrorCode());
        verify(entries, never()).saveDraft(any());
    }

    @Test
    void completeTaskReportsInvalidConfidenceAsBadRequest() {
        when(tasks.findById(1L)).thenReturn(Optional.of(task("IN_PROGRESS", 4L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeTask(
                        1L, 2L, "answer", "CERTAIN", Collections.emptyList(), 4L, null));
        assertEquals(ErrorCode.REQUEST_PARAMETER_INVALID, error.getErrorCode());
    }

    @Test
    void completeTaskRejectsEvidenceOutsideEventOrTopic() {
        when(tasks.findById(1L)).thenReturn(Optional.of(task("IN_PROGRESS", 4L)));
        EvidenceItem unrelated = new EvidenceItem();
        unrelated.setId(8L);
        unrelated.setEventId(99L);
        when(evidence.findById(8L)).thenReturn(Optional.of(unrelated));
        when(topicEvents.isLinked(2L, 99L)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeTask(
                        1L, 2L, "answer", "MEDIUM", Arrays.asList(8L), 4L, null));
        assertEquals(ErrorCode.REQUEST_PARAMETER_INVALID, error.getErrorCode());
        verify(entries, never()).saveDraft(any());
    }

    @Test
    void completeTaskFinalizesEntryTransitionsTaskLinksEventAndEnqueuesProjection() {
        LearningTask task = task("IN_PROGRESS", 4L);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        EvidenceItem item = new EvidenceItem();
        item.setId(8L);
        item.setEventId(11L);
        when(evidence.findById(8L)).thenReturn(Optional.of(item));
        when(entries.findDraftByTaskId(1L)).thenReturn(Optional.empty());

        KnowledgeEntry draft = entry("DRAFT", 0L);
        draft.setId(100L);
        when(entries.saveDraft(any())).thenReturn(draft);
        when(entries.finalizeDraft(100L, 0L)).thenReturn(true);
        KnowledgeEntry completed = entry("FINAL", 1L);
        completed.setId(100L);
        when(entries.findById(100L)).thenReturn(Optional.of(completed));
        when(tasks.transition(eq(1L), eq("IN_PROGRESS"), eq("DONE"), eq(2L),
                any(), eq(null), eq("RECORDED"), eq(4L))).thenReturn(true);
        KnowledgeProjectionJob job = new KnowledgeProjectionJob();
        job.setId(200L);
        job.setTopicId(2L);
        job.setEntryId(100L);
        when(projectionJobs.enqueue(2L, 100L)).thenReturn(job);

        KnowledgeEntry result = service.completeTask(
                1L, 2L, "answer", "HIGH", Arrays.asList(8L), 4L, null);

        assertEquals("FINAL", result.getEntryStatus());
        verify(entries).linkEvidence(100L, Arrays.asList(8L));
        verify(topicEvents).link(2L, 11L, "LEARNING_SOURCE");
        verify(events).publishEvent(any(KnowledgeProjectionRequested.class));
    }

    @Test
    void completeTaskReportsRevisionConflictBeforePublishingProjection() {
        LearningTask task = task("IN_PROGRESS", 4L);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(entries.findDraftByTaskId(1L)).thenReturn(Optional.empty());
        KnowledgeEntry draft = entry("DRAFT", 0L);
        draft.setId(100L);
        when(entries.saveDraft(any())).thenReturn(draft);
        when(entries.finalizeDraft(100L, 0L)).thenReturn(true);
        when(tasks.transition(eq(1L), eq("IN_PROGRESS"), eq("DONE"), eq(2L),
                any(), eq(null), eq("RECORDED"), eq(4L))).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeTask(
                        1L, 2L, "answer", "HIGH", Collections.emptyList(), 4L, null));
        assertEquals(ErrorCode.BUSINESS_CONFLICT, error.getErrorCode());
        verify(projectionJobs, never()).enqueue(anyLong(), anyLong());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void saveDraftUsesIndependentTaskAndEntryRevisions() {
        LearningTask task = task("IN_PROGRESS", 4L);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        KnowledgeEntry draft = entry("DRAFT", 2L);
        draft.setId(100L);
        KnowledgeEntry updated = entry("DRAFT", 3L);
        updated.setId(100L);
        when(entries.findDraftByTaskId(1L)).thenReturn(Optional.of(draft));
        when(entries.updateDraft(100L, "revised answer", "HIGH", 2L)).thenReturn(true);
        when(entries.findById(100L)).thenReturn(Optional.of(updated));

        KnowledgeEntry result = service.saveDraft(
                1L, 2L, "revised answer", "HIGH", Collections.emptyList(), 4L, 2L);

        assertEquals(3L, result.getRevision());
        verify(entries).updateDraft(100L, "revised answer", "HIGH", 2L);
    }

    @Test
    void dismissInProgressTaskRequiresReason() {
        when(tasks.findById(1L)).thenReturn(Optional.of(task("IN_PROGRESS", 4L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.dismissTask(1L, " ", 4L));
        assertEquals(ErrorCode.REQUEST_PARAMETER_INVALID, error.getErrorCode());
    }

    private LearningTask task(String status, long revision) {
        LearningTask task = new LearningTask();
        task.setId(1L);
        task.setEventId(11L);
        task.setTopicId(2L);
        task.setQuestion("What did we learn?");
        task.setStatus(status);
        task.setRevision(revision);
        return task;
    }

    private KnowledgeEntry entry(String status, long revision) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setTopicId(2L);
        entry.setLearningTaskId(1L);
        entry.setEntryType("ANSWER");
        entry.setEntryStatus(status);
        entry.setRevision(revision);
        return entry;
    }
}
