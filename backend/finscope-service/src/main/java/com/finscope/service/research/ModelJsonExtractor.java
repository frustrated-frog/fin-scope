package com.finscope.service.research;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Extracts one complete JSON object from provider text without depending on Markdown conventions. */
public final class ModelJsonExtractor {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private ModelJsonExtractor() {
    }

    public static String extractObject(String raw, int maxCharacters) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("模型返回空内容");
        }
        if (raw.length() > maxCharacters) {
            throw new IllegalArgumentException("模型输出超过字符上限");
        }
        boolean foundOpeningBrace = false;
        boolean foundIncompleteObject = false;
        for (int start = 0; start < raw.length(); start++) {
            if (raw.charAt(start) != '{') continue;
            foundOpeningBrace = true;
            int end = objectEnd(raw, start);
            if (end < 0) {
                foundIncompleteObject = true;
                continue;
            }
            String candidate = raw.substring(start, end + 1);
            try {
                JsonNode parsed = JSON.readTree(candidate);
                if (parsed != null && parsed.isObject()) return candidate;
            } catch (Exception ignored) {
                // Provider prose may contain example braces before the actual JSON object.
            }
        }
        if (!foundOpeningBrace) throw new IllegalArgumentException("模型输出中没有 JSON 对象");
        if (foundIncompleteObject) throw new IllegalArgumentException("模型输出中的 JSON 对象不完整");
        throw new IllegalArgumentException("模型输出中没有可解析的 JSON 对象");
    }

    private static int objectEnd(String raw, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }
}
