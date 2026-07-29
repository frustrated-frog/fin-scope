package com.finscope.service.research.report;

import java.util.Collections;
import java.util.List;

public final class ResearchClaim {
    private final String rawText;
    private final String text;
    private final List<String> evidenceRefs;
    private final List<String> numbers;

    ResearchClaim(String rawText, String text, List<String> evidenceRefs, List<String> numbers) {
        this.rawText = rawText;
        this.text = text;
        this.evidenceRefs = Collections.unmodifiableList(evidenceRefs);
        this.numbers = Collections.unmodifiableList(numbers);
    }

    public String getRawText() { return rawText; }
    public String getText() { return text; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public List<String> getNumbers() { return numbers; }
}
