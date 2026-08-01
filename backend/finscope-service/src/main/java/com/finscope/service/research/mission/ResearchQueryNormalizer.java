package com.finscope.service.research.mission;

public final class ResearchQueryNormalizer {
    private ResearchQueryNormalizer() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public static String normalizeNullable(String value) {
        return value == null ? null : normalize(value);
    }
}
