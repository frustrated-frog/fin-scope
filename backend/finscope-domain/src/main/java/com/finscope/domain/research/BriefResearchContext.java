package com.finscope.domain.research;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class BriefResearchContext {
    /**
     * 简报日期。
     */
    private LocalDate briefDate;
    /**
     * 事件列表。
     */
    private List<EventCluster> events = Collections.emptyList();
    /**
     * 证据条目列表。
     */
    private List<EvidenceItem> evidenceItems = Collections.emptyList();
    /**
     * 学习任务列表。
     */
    private List<LearningTask> learningTasks = Collections.emptyList();
    /**
     * 内容选题列表。
     */
    private List<ContentIdea> contentIdeas = Collections.emptyList();

    public LocalDate getBriefDate() {
        return briefDate;
    }

    public void setBriefDate(LocalDate briefDate) {
        this.briefDate = briefDate;
    }

    public List<EventCluster> getEvents() {
        return events;
    }

    public void setEvents(List<EventCluster> events) {
        this.events = events == null ? Collections.<EventCluster>emptyList() : events;
    }

    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    public void setEvidenceItems(List<EvidenceItem> evidenceItems) {
        this.evidenceItems = evidenceItems == null ? Collections.<EvidenceItem>emptyList() : evidenceItems;
    }

    public List<LearningTask> getLearningTasks() {
        return learningTasks;
    }

    public void setLearningTasks(List<LearningTask> learningTasks) {
        this.learningTasks = learningTasks == null ? Collections.<LearningTask>emptyList() : learningTasks;
    }

    public List<ContentIdea> getContentIdeas() {
        return contentIdeas;
    }

    public void setContentIdeas(List<ContentIdea> contentIdeas) {
        this.contentIdeas = contentIdeas == null ? Collections.<ContentIdea>emptyList() : contentIdeas;
    }

    public boolean isEmpty() {
        return events.isEmpty() && evidenceItems.isEmpty() && learningTasks.isEmpty() && contentIdeas.isEmpty();
    }
}
