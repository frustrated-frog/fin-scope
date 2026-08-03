package com.finscope.service.research.report;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchReportSectionParser {
    private static final Pattern MARKER = Pattern.compile("<<<([A-Z][A-Z0-9_]*)>>>");
    private static final int MAX_SECTION_CHARACTERS = 6_000;

    public Map<String, String> parse(String raw, Set<String> allowedSections) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        String value = raw == null ? "" : raw;
        Matcher marker = MARKER.matcher(value);
        while (marker.find()) {
            String name = marker.group(1);
            if ("END".equals(name) || allowedSections == null || !allowedSections.contains(name)
                    || result.containsKey(name)) {
                continue;
            }
            int contentStart = marker.end();
            Matcher next = MARKER.matcher(value);
            int contentEnd = value.length();
            if (next.find(contentStart)) contentEnd = next.start();
            String content = clean(value.substring(contentStart, contentEnd));
            if (!content.isEmpty()) result.put(name, content);
        }
        return result;
    }

    private String clean(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() <= MAX_SECTION_CHARACTERS) return clean;
        return ResearchFactText.completeExcerpt(clean, MAX_SECTION_CHARACTERS);
    }
}
