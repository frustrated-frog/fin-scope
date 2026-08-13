package com.finscope.service.radar;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 一轮热点生产中可复用的确定性信号特征。 */
public final class RadarSignalFeatures {
    private final String normalizedTitle;
    private final String normalizedContent;
    private final String category;
    private final Set<String> subjects;
    private final Set<String> actions;
    private final Set<String> variables;
    private final Set<String> directions;
    private final Set<String> entities;
    private final Set<String> numericFacts;
    private final LocalDateTime eventTime;

    RadarSignalFeatures(String normalizedTitle, String normalizedContent, String category,
                        Set<String> subjects, Set<String> actions, Set<String> variables,
                        Set<String> directions, Set<String> entities, Set<String> numericFacts,
                        LocalDateTime eventTime) {
        this.normalizedTitle = normalizedTitle;
        this.normalizedContent = normalizedContent;
        this.category = category;
        this.subjects = immutable(subjects);
        this.actions = immutable(actions);
        this.variables = immutable(variables);
        this.directions = immutable(directions);
        this.entities = immutable(entities);
        this.numericFacts = immutable(numericFacts);
        this.eventTime = eventTime;
    }

    private Set<String> immutable(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(values));
    }

    public String getNormalizedTitle() { return normalizedTitle; }
    public String getNormalizedContent() { return normalizedContent; }
    public String getCategory() { return category; }
    public Set<String> getSubjects() { return subjects; }
    public Set<String> getActions() { return actions; }
    public Set<String> getVariables() { return variables; }
    public Set<String> getDirections() { return directions; }
    public Set<String> getEntities() { return entities; }
    public Set<String> getNumericFacts() { return numericFacts; }
    public LocalDateTime getEventTime() { return eventTime; }
}
