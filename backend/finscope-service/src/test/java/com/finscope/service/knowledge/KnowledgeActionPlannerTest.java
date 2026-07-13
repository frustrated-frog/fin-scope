package com.finscope.service.knowledge;

import com.finscope.domain.knowledge.KnowledgeAction;
import com.finscope.domain.knowledge.KnowledgeActionCandidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeActionPlannerTest {
    private final KnowledgeActionPlanner planner = new KnowledgeActionPlanner();

    @Test
    void prioritizesWorkDeterministicallyAndLimitsOutputToThree() {
        List<KnowledgeActionCandidate> candidates = Arrays.asList(
                candidate("CHECK_NEW_EVIDENCE", 4L, null, 4L, "AI Agents", 90),
                candidate("START_TASK", 3L, 103L, 3L, "Learn valuation", 95),
                candidate("REVIEW_TOPIC", 2L, null, 2L, "Robotics", 0),
                candidate("CONTINUE_TASK", 1L, 101L, 1L, "Finish agent notes", 60),
                candidate("CONTINUE_TASK", 5L, 102L, 5L, "Lower priority work", 20)
        );

        List<KnowledgeAction> actions = planner.plan(candidates);

        assertEquals(3, actions.size());
        assertEquals("CONTINUE_TASK", actions.get(0).getType());
        assertEquals(101L, actions.get(0).getTaskId());
        assertEquals("CONTINUE_TASK", actions.get(1).getType());
        assertEquals(102L, actions.get(1).getTaskId());
        assertEquals("REVIEW_TOPIC", actions.get(2).getType());
        for (KnowledgeAction action : actions) {
            assertFalse(action.getReason().trim().isEmpty());
            assertFalse(action.getRouteTarget().trim().isEmpty());
            assertFalse(action.getSourceLabel().trim().isEmpty());
        }
    }

    @Test
    void usesPriorityToOrderTasksWithinTheSameActionType() {
        List<KnowledgeAction> actions = planner.plan(Arrays.asList(
                candidate("START_TASK", 1L, 11L, 1L, "Normal", 50),
                candidate("START_TASK", 2L, 12L, 2L, "Urgent", 90)
        ));

        assertEquals(12L, actions.get(0).getTaskId());
        assertEquals(11L, actions.get(1).getTaskId());
    }

    @Test
    void returnsEmptyWhenThereIsNoRealUserAction() {
        assertTrue(planner.plan(Collections.emptyList()).isEmpty());
    }

    private KnowledgeActionCandidate candidate(String type, long stableId, Long taskId,
                                               Long topicId, String title, int priority) {
        KnowledgeActionCandidate candidate = new KnowledgeActionCandidate();
        candidate.setType(type);
        candidate.setStableId(stableId);
        candidate.setTaskId(taskId);
        candidate.setTopicId(topicId);
        candidate.setTitle(title);
        candidate.setPriority(priority);
        candidate.setSortAt(LocalDateTime.of(2026, 7, 13, 10, 0).plusMinutes(stableId));
        return candidate;
    }
}
