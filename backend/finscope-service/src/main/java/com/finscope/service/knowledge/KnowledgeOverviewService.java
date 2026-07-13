package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeQueryRepository;
import com.finscope.domain.knowledge.KnowledgeOverview;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class KnowledgeOverviewService {
    private final KnowledgeQueryRepository queries;
    private final KnowledgeActionPlanner planner;
    private final Clock clock;

    @Autowired
    public KnowledgeOverviewService(KnowledgeQueryRepository queries,
                                    KnowledgeActionPlanner planner) {
        this(queries, planner, Clock.systemDefaultZone());
    }

    KnowledgeOverviewService(KnowledgeQueryRepository queries,
                             KnowledgeActionPlanner planner,
                             Clock clock) {
        this.queries = queries;
        this.planner = planner;
        this.clock = clock;
    }

    public KnowledgeOverview load() {
        KnowledgeQueryRepository.OverviewSnapshot snapshot =
                queries.loadOverview(LocalDateTime.now(clock));
        KnowledgeOverview overview = new KnowledgeOverview();
        overview.setAcceptedTaskCount(snapshot.getAcceptedTaskCount());
        overview.setSuggestedTaskCount(snapshot.getSuggestedTaskCount());
        overview.setDueReviewCount(snapshot.getDueReviewCount());
        overview.setActiveTopicCount(snapshot.getActiveTopicCount());
        overview.setActiveTopics(snapshot.getActiveTopics());
        overview.setRecentEntries(snapshot.getRecentEntries());
        overview.setActions(planner.plan(snapshot.getActionCandidates()));
        return overview;
    }

    public PageResponse<Topic> topicsPage(String lifecycle, String mastery,
                                          boolean dueOnly, String query,
                                          int page, int pageSize) {
        return queries.findTopicsPage(lifecycle, mastery, dueOnly, query,
                page, pageSize, LocalDateTime.now(clock));
    }

    public PageResponse<LearningTask> tasksPage(String status, Long topicId,
                                                String query, int page,
                                                int pageSize) {
        return queries.findLearningTasksPage(status, topicId, query, page, pageSize);
    }

    public PageResponse<Topic> dueReviews(int page, int pageSize) {
        return queries.findTopicsPage("ACTIVE", null, true, null,
                page, pageSize, LocalDateTime.now(clock));
    }
}
