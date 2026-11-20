package com.finscope.domain.news;

import java.util.List;

/** 同一份可展开的讲解随报道版本或事件证据版本保存。 */
public record NewsReading(String lead, List<Concept> concepts, List<Section> sections) {
    public static final int FORMAT_VERSION = 4;

    public record Concept(String term, String meaning) {
    }

    public record Section(String question, String answer) {
    }
}
