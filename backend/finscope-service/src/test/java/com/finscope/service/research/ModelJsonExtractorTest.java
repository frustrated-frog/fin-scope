package com.finscope.service.research;

import com.finscope.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelJsonExtractorTest {
    @Test
    void extractsOneObjectFromReasoningProseAndMarkdownFence() {
        String raw = "I will return JSON now.\n```json\n{\"summary\":\"含有 } 字符\",\"ok\":true}\n```";

        assertEquals("{\"summary\":\"含有 } 字符\",\"ok\":true}",
                ModelJsonExtractor.extractObject(raw, 1000));
    }

    @Test
    void rejectsTruncatedObjectsWithoutGuessingMissingContent() {
        assertThrows(BusinessException.class,
                () -> ModelJsonExtractor.extractObject("{\"summary\":\"未结束", 1000));
    }

    @Test
    void skipsInvalidExampleBracesBeforeTheActualJsonObject() {
        String raw = "Use this shape: {missionTaskKey: string}. Result: "
                + "{\"missionTaskKey\":\"search_counter\"}";

        assertEquals("{\"missionTaskKey\":\"search_counter\"}",
                ModelJsonExtractor.extractObject(raw, 1000));
    }
}
