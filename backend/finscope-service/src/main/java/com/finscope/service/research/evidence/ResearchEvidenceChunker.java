package com.finscope.service.research.evidence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ResearchEvidenceChunker {
    private static final int DEFAULT_MAX_CHARS = 900;
    private final int maxChars;
    private final int overlapChars;

    public ResearchEvidenceChunker() {
        this(DEFAULT_MAX_CHARS, 120);
    }

    public ResearchEvidenceChunker(int maxChars, int overlapChars) {
        if (maxChars < 60 || overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("证据分块参数无效");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<ResearchEvidenceChunk> chunk(String body) {
        String normalized = body == null ? "" : body.replace("\r", "").trim();
        if (normalized.isEmpty()) return Collections.emptyList();
        List<String> units = units(normalized);
        List<ResearchEvidenceChunk> result = new ArrayList<ResearchEvidenceChunk>();
        for (String unit : units) {
            if (unit.length() > maxChars) {
                splitLong(result, unit);
            } else {
                add(result, unit);
            }
        }
        return result;
    }

    private List<String> units(String body) {
        List<String> result = new ArrayList<String>();
        for (String paragraph : body.split("\\n\\s*\\n|(?<=[。！？.!?])\\s+")) {
            String compact = paragraph.replaceAll("\\s+", " ").trim();
            if (!compact.isEmpty()) result.add(compact);
        }
        return result;
    }

    private void splitLong(List<ResearchEvidenceChunk> result, String value) {
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + maxChars);
            add(result, value.substring(start, end));
            if (end == value.length()) break;
            start = Math.max(start + 1, end - overlapChars);
        }
    }

    private void add(List<ResearchEvidenceChunk> result, String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (!compact.isEmpty()) result.add(new ResearchEvidenceChunk(result.size(), compact));
    }

}
