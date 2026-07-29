package com.finscope.service.research.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResearchFactTextTest {
    @Test
    void selectsCompleteSentencesWithoutEllipsis() {
        String text = "第一句说明事实。第二句补充影响。第三句不应进入。";

        String result = ResearchFactText.completeExcerpt(text, 18);

        assertEquals("第一句说明事实。第二句补充影响。", result);
        assertFalse(result.contains("…"));
    }

    @Test
    void keepsTheWholeFactWhenNoSafeBoundaryExists() {
        String text = "一段没有句末标点但必须保持完整的事实材料";

        assertEquals(text, ResearchFactText.completeExcerpt(text, 10));
    }
}
