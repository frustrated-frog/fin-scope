package com.finscope.service.research.report;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResearchReportSectionParserTest {
    private final ResearchReportSectionParser parser = new ResearchReportSectionParser();

    @Test
    void extractsOnlyAllowedNamedSectionsAndKeepsTheFirstDuplicate() {
        String output = "说明文字\n<<<DIRECT_ANSWER>>>\n第一版回答\n<<<END>>>\n"
                + "<<<UNKNOWN_SLOT>>>\n忽略\n<<<END>>>\n"
                + "<<<DIRECT_ANSWER>>>\n第二版回答\n<<<END>>>";

        Map<String, String> result = parser.parse(output,
                new HashSet<String>(Arrays.asList("DIRECT_ANSWER")));

        assertEquals("第一版回答", result.get("DIRECT_ANSWER"));
        assertFalse(result.containsKey("UNKNOWN_SLOT"));
    }

    @Test
    void recoversASectionWithoutEndMarkerAtTheNextKnownMarker() {
        String output = "<<<DIRECT_ANSWER>>>\n阶段性回答\n"
                + "<<<CONFIDENCE_BASIS>>>\n证据覆盖有限\n<<<END>>>";

        Map<String, String> result = parser.parse(output,
                new HashSet<String>(Arrays.asList("DIRECT_ANSWER", "CONFIDENCE_BASIS")));

        assertEquals("阶段性回答", result.get("DIRECT_ANSWER"));
        assertEquals("证据覆盖有限", result.get("CONFIDENCE_BASIS"));
    }

    @Test
    void returnsNoSectionsForProviderJsonOrUnmarkedProse() {
        assertEquals(0, parser.parse("{\"directAnswer\":\"旧协议\"}",
                new HashSet<String>(Arrays.asList("DIRECT_ANSWER"))).size());
        assertEquals(0, parser.parse("普通解释文本",
                new HashSet<String>(Arrays.asList("DIRECT_ANSWER"))).size());
    }
}
