package com.finscope.service.knowledge;

import com.finscope.domain.knowledge.KnowledgeEnums.LearningStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single authority for learning-task lifecycle transitions.
 */
@Component
public class LearningTaskPolicy {
    private static final Map<LearningStatus, Set<LearningStatus>> TRANSITIONS = buildTransitions();

    public boolean canTransition(String from, String to) {
        return canTransition(LearningStatus.parse(from), LearningStatus.parse(to));
    }

    public boolean canTransition(LearningStatus from, LearningStatus to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Transition endpoints must not be null");
        }
        return TRANSITIONS.get(from).contains(to);
    }

    private static Map<LearningStatus, Set<LearningStatus>> buildTransitions() {
        EnumMap<LearningStatus, Set<LearningStatus>> transitions = new EnumMap<>(LearningStatus.class);
        transitions.put(LearningStatus.SUGGESTED,
                immutableSet(LearningStatus.TODO, LearningStatus.DISMISSED));
        transitions.put(LearningStatus.TODO,
                immutableSet(LearningStatus.IN_PROGRESS, LearningStatus.DISMISSED));
        transitions.put(LearningStatus.IN_PROGRESS,
                immutableSet(LearningStatus.TODO, LearningStatus.DONE, LearningStatus.DISMISSED));
        transitions.put(LearningStatus.DONE, Collections.emptySet());
        transitions.put(LearningStatus.DISMISSED, Collections.emptySet());
        return Collections.unmodifiableMap(transitions);
    }

    private static Set<LearningStatus> immutableSet(LearningStatus first, LearningStatus... rest) {
        EnumSet<LearningStatus> statuses = EnumSet.of(first, rest);
        return Collections.unmodifiableSet(statuses);
    }
}
