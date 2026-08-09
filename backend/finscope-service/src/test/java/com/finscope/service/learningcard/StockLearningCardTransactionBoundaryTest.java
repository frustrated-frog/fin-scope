package com.finscope.service.learningcard;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNull;

class StockLearningCardTransactionBoundaryTest {
    @Test
    void keepsSqliteLearningCardEntryPointsOutOfLongTransactions() throws Exception {
        assertNull(StockLearningCardService.class.getMethod("start", String.class).getAnnotation(Transactional.class));
        assertNull(StockLearningCardService.class.getMethod("get", String.class).getAnnotation(Transactional.class));
    }
}
