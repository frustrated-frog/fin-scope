package com.finscope.service.topic;

import java.util.List;

public class TopicExtraction {
    private final String primaryTopicName;
    private final String description;
    private final List<String> terms;
    private final List<String> learningQuestions;

    public TopicExtraction(String primaryTopicName, String description, List<String> terms, List<String> learningQuestions) {
        this.primaryTopicName = primaryTopicName;
        this.description = description;
        this.terms = terms;
        this.learningQuestions = learningQuestions;
    }

    public String getPrimaryTopicName() {
        return primaryTopicName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTerms() {
        return terms;
    }

    public List<String> getLearningQuestions() {
        return learningQuestions;
    }
}
