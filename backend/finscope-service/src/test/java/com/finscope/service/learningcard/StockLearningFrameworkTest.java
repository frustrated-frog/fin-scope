package com.finscope.service.learningcard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StockLearningFrameworkTest {
    @Test void exposesTheSixFixedLiujieDimensions() {
        assertEquals(6, StockLearningFramework.dimensions().size());
        assertEquals("SPACE", StockLearningFramework.dimensions().get(0));
        assertEquals("COUNTER_CASE", StockLearningFramework.dimensions().get(5));
        assertFalse(StockLearningFramework.isAllowedText("建议买入并目标价100元"));
        assertFalse(StockLearningFramework.isAllowedText("可以考虑建仓"));
        assertFalse(StockLearningFramework.isAllowedText("建议持有，预计年化收益30%"));
        assertFalse(StockLearningFramework.isAllowedText("上方目标位仍有空间，可以做多"));
    }
}
