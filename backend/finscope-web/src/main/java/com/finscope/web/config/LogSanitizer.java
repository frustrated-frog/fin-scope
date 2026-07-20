package com.finscope.web.config;

final class LogSanitizer {
    private LogSanitizer() {
    }

    static String clean(String value, int maximumLength) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (cleaned.isEmpty()) {
            return "-";
        }
        if (maximumLength > 0 && cleaned.length() > maximumLength) {
            return cleaned.substring(0, maximumLength) + "...";
        }
        return cleaned;
    }
}
