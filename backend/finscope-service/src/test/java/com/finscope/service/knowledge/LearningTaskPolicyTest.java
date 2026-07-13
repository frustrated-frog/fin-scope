package com.finscope.service.knowledge;

import com.finscope.domain.knowledge.KnowledgeEnums;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningTaskPolicyTest {
    private final LearningTaskPolicy policy = new LearningTaskPolicy();

    @Test
    void allowsOnlyDeclaredTransitions() {
        assertTrue(policy.canTransition("SUGGESTED", "TODO"));
        assertTrue(policy.canTransition("SUGGESTED", "DISMISSED"));
        assertTrue(policy.canTransition("TODO", "IN_PROGRESS"));
        assertTrue(policy.canTransition("IN_PROGRESS", "TODO"));
        assertTrue(policy.canTransition("IN_PROGRESS", "DONE"));

        assertFalse(policy.canTransition("SUGGESTED", "DONE"));
        assertFalse(policy.canTransition("DONE", "TODO"));
        assertFalse(policy.canTransition("DISMISSED", "TODO"));
    }

    @Test
    void rejectsUnknownPersistedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeEnums.LearningStatus.parse("REVIEWING"));
    }

    @Test
    void rejectsUnknownTransitionEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.canTransition("UNKNOWN", "TODO"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.canTransition("TODO", "UNKNOWN"));
    }
}
