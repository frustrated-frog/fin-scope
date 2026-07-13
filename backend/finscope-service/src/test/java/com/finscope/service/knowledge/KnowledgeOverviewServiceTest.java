package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeQueryRepository;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeOverviewServiceTest {
    private final KnowledgeQueryRepository queries = mock(KnowledgeQueryRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC);
    private final KnowledgeOverviewService service = new KnowledgeOverviewService(
            queries, mock(KnowledgeActionPlanner.class), clock);

    @Test
    void delegatesBoundedTopicAndTaskPagesToReadModel() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 13, 6, 0);
        PageResponse<Topic> topics = PageResponse.of(
                Collections.<Topic>emptyList(), 0, 1, 25);
        PageResponse<LearningTask> tasks = PageResponse.of(
                Collections.<LearningTask>emptyList(), 0, 2, 10);
        when(queries.findTopicsPage("ACTIVE", "BUILDING", false,
                "agent", 1, 25, now)).thenReturn(topics);
        when(queries.findLearningTasksPage("TODO", 3L, "why", 2, 10))
                .thenReturn(tasks);

        assertSame(topics, service.topicsPage(
                "ACTIVE", "BUILDING", false, "agent", 1, 25));
        assertSame(tasks, service.tasksPage("TODO", 3L, "why", 2, 10));
    }

    @Test
    void dueReviewsUsesActiveTopicsAndCurrentClock() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 13, 6, 0);
        PageResponse<Topic> due = PageResponse.of(
                Collections.<Topic>emptyList(), 0, 0, 20);
        when(queries.findTopicsPage("ACTIVE", null, true,
                null, 0, 20, now)).thenReturn(due);

        assertSame(due, service.dueReviews(0, 20));
        verify(queries).findTopicsPage("ACTIVE", null, true,
                null, 0, 20, now);
    }
}
