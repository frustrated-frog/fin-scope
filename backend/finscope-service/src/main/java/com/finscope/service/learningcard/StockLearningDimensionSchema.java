package com.finscope.service.learningcard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StockLearningDimensionSchema {
    private final String dimensionCode;
    private final String ratingLabel;
    private final List<SectionDefinition> requiredSections;
    private final List<SectionDefinition> optionalSections;
    private final Map<String, SectionDefinition> sectionsByKey;

    public StockLearningDimensionSchema(String dimensionCode, String ratingLabel,
                                        List<SectionDefinition> requiredSections,
                                        List<SectionDefinition> optionalSections) {
        this.dimensionCode = dimensionCode;
        this.ratingLabel = ratingLabel;
        this.requiredSections = immutable(requiredSections);
        this.optionalSections = immutable(optionalSections);
        Map<String, SectionDefinition> definitions = new LinkedHashMap<String, SectionDefinition>();
        for (SectionDefinition section : allSections()) {
            if (definitions.put(section.getKey(), section) != null) {
                throw new IllegalArgumentException("学习卡栏目代码重复：" + section.getKey());
            }
        }
        this.sectionsByKey = Collections.unmodifiableMap(definitions);
    }

    public String getDimensionCode() { return dimensionCode; }
    public String getRatingLabel() { return ratingLabel; }
    public List<SectionDefinition> getRequiredSections() { return requiredSections; }
    public List<SectionDefinition> getOptionalSections() { return optionalSections; }
    public SectionDefinition section(String key) { return sectionsByKey.get(key); }

    public List<String> requiredKeys() {
        return keys(requiredSections);
    }

    public List<String> optionalKeys() {
        return keys(optionalSections);
    }

    public List<SectionDefinition> allSections() {
        List<SectionDefinition> values = new ArrayList<SectionDefinition>(requiredSections);
        values.addAll(optionalSections);
        return Collections.unmodifiableList(values);
    }

    public int orderOf(String key) {
        List<SectionDefinition> definitions = allSections();
        for (int index = 0; index < definitions.size(); index++) {
            if (definitions.get(index).getKey().equals(key)) {
                return index;
            }
        }
        return -1;
    }

    private List<SectionDefinition> immutable(List<SectionDefinition> values) {
        return Collections.unmodifiableList(new ArrayList<SectionDefinition>(values));
    }

    private List<String> keys(List<SectionDefinition> definitions) {
        List<String> keys = new ArrayList<String>();
        for (SectionDefinition definition : definitions) {
            keys.add(definition.getKey());
        }
        return Collections.unmodifiableList(keys);
    }

    public static SectionDefinition section(String key, String title) {
        return new SectionDefinition(key, title);
    }

    public static final class SectionDefinition {
        private final String key;
        private final String title;

        private SectionDefinition(String key, String title) {
            this.key = key;
            this.title = title;
        }

        public String getKey() { return key; }
        public String getTitle() { return title; }
    }
}
