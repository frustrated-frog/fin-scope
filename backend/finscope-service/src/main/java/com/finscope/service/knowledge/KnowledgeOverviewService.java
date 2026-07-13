package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeQueryRepository;
import com.finscope.domain.knowledge.KnowledgeOverview;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class KnowledgeOverviewService {
    private final KnowledgeQueryRepository queries;
    private final KnowledgeActionPlanner planner;
    private final Clock clock;

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
}
