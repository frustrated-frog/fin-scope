package com.finscope.service.research.report;

import com.finscope.common.util.WebContentText;

/**
 * Cleans research facts and limits prompt size only at complete sentence boundaries.
 */
final class ResearchFactText {
    private static final int BOUNDARY_LOOKAHEAD = 200;

    private ResearchFactText() {
    }

    static String completeExcerpt(String value, int preferredMaximum) {
        String clean = clean(value);
        if (preferredMaximum <= 0 || clean.length() <= preferredMaximum) return clean;
        int boundary = lastBoundary(clean, preferredMaximum);
        int minimumUseful = Math.min(80, Math.max(1, preferredMaximum / 2));
        if (boundary < minimumUseful) {
            boundary = nextBoundary(clean, preferredMaximum,
                    Math.min(clean.length(), preferredMaximum + BOUNDARY_LOOKAHEAD));
        }
        return boundary > 0 ? clean.substring(0, boundary).trim()
                : clean.substring(0, Math.min(preferredMaximum, clean.length())).trim();
    }

    private static String clean(String value) {
        return WebContentText.clean(value)
                .replaceAll("!\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?i)\\[S\\d+\\]\\s*(?:-\\s*\\d+\\s*/)?\\s*\\[?", "")
                .replaceAll("\\[\\s*]\\s*\\([^)]*\\)", "")
                .replaceAll("https?://\\S+", "")
                .replace("（已截断）", "")
                .replace("…", "")
                .replace("...", "")
                .replaceFirst("^#+\\s*", "")
                .replaceAll("[*_`~]+", "")
                .replaceAll("(?:^|\\s)(?:移动版|网页版|首页|快讯|新闻|要闻|财经|评论)(?=\\s|[)\\]\\[(*]|$)", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int lastBoundary(String value, int maximum) {
        int boundary = -1;
        int end = Math.min(value.length(), maximum);
        for (int index = 0; index < end; index++) {
            if (isBoundary(value, index)) {
                boundary = index + 1;
            }
        }
        return boundary;
    }

    private static int nextBoundary(String value, int start, int end) {
        for (int index = Math.max(0, start); index < end; index++) {
            if (isBoundary(value, index)) {
                return index + 1;
            }
        }
        return -1;
    }

    private static boolean isBoundary(String text, int index) {
        char value = text.charAt(index);
        if (value == '.' && index > 0 && index + 1 < text.length()
                && Character.isDigit(text.charAt(index - 1)) && Character.isDigit(text.charAt(index + 1))) {
            return false;
        }
        return value == '。' || value == '！' || value == '？' || value == '.' || value == '!'
                || value == '?' || value == '；' || value == ';';
    }
}
