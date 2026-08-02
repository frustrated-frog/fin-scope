package com.finscope.service.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ModelJsonShapeNormalizer {
    private final ObjectMapper objectMapper;

    public ModelJsonShapeNormalizer(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        this.objectMapper = objectMapper;
    }

    public void normalizeTextFields(ObjectNode parent, String... fields) throws Exception {
        for (String field : fields) {
            JsonNode value = parent.get(field);
            if (value != null && !value.isNull() && !value.isTextual()) {
                parent.put(field, objectMapper.writeValueAsString(value));
            }
        }
    }

    public void normalizeStringArrayFields(ObjectNode parent, String... fields) throws Exception {
        for (String field : fields) {
            normalizeStringArrayField(parent, field);
        }
    }

    public void normalizeStringArrayField(ObjectNode parent, String field) throws Exception {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull() || value.isArray()) {
            return;
        }
        if (!value.isTextual()) {
            return;
        }
        String text = value.asText().trim();
        JsonNode embedded = parseEmbedded(text);
        if (embedded != null && embedded.isArray()) {
            parent.set(field, embedded);
            return;
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        if (!text.isEmpty()) {
            for (String item : text.split("[；;\\r\\n]+")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        parent.set(field, normalized);
    }

    public void normalizeObjectFields(ObjectNode parent, String... fields) throws Exception {
        for (String field : fields) {
            JsonNode value = parent.get(field);
            if (value == null || value.isNull() || value.isObject() || !value.isTextual()) {
                continue;
            }
            String text = value.asText().trim();
            if (text.isEmpty()) {
                parent.set(field, objectMapper.createObjectNode());
                continue;
            }
            JsonNode embedded = parseEmbedded(text);
            if (embedded != null && embedded.isObject()) {
                parent.set(field, embedded);
            }
        }
    }

    public void normalizeObjectArrayField(ObjectNode parent, String field) throws Exception {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull() || value.isArray()) {
            return;
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        if (value.isObject()) {
            normalized.add(value);
            parent.set(field, normalized);
            return;
        }
        if (!value.isTextual()) {
            return;
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            parent.set(field, normalized);
            return;
        }
        JsonNode embedded = parseEmbedded(text);
        if (embedded != null && embedded.isArray()) {
            parent.set(field, embedded);
        } else if (embedded != null && embedded.isObject()) {
            normalized.add(embedded);
            parent.set(field, normalized);
        }
    }

    private JsonNode parseEmbedded(String value) throws Exception {
        if (value == null || value.length() < 2) {
            return null;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (!((first == '[' && last == ']') || (first == '{' && last == '}'))) {
            return null;
        }
        return objectMapper.readTree(value);
    }
}
