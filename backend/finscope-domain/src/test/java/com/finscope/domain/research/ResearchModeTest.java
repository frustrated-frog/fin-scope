package com.finscope.domain.research;

import com.finscope.common.enums.research.ResearchMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchModeTest {
    @Test
    void defaultsToDeepAndProjectsBoundedBudgets() {
        assertEquals(ResearchMode.DEEP, ResearchMode.from(null));
        assertEquals(ResearchMode.DEEP, ResearchMode.from(""));
        assertEquals(2, ResearchMode.QUICK.getSearchActionBudget());
        assertEquals(2, ResearchMode.QUICK.getFullTextReadsPerSearch());
        assertEquals(1, ResearchMode.QUICK.getMaxConcurrency());
        assertEquals(5, ResearchMode.QUICK.getMaxIterations());
        assertEquals(6, ResearchMode.DEEP.getSearchActionBudget());
        assertEquals(3, ResearchMode.DEEP.getFullTextReadsPerSearch());
        assertEquals(3, ResearchMode.DEEP.getMaxConcurrency());
        assertEquals(10, ResearchMode.DEEP.getMaxIterations());
    }
}
