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
    void boundsAnUnpunctuatedFactInsteadOfPassingItThroughUnbounded() {
        String text = "一段没有句末标点但必须保持完整的事实材料";

        assertEquals(text.substring(0, 10), ResearchFactText.completeExcerpt(text, 10));
    }
    @Test
    void neverTreatsADecimalPointAsTheEndOfAFact() {
        String text = "公司仅持长鑫科技1.8%股权，不存在控制关系。";
        assertEquals(text, ResearchFactText.completeExcerpt(text, 10));
    }

}
