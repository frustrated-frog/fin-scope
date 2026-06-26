package com.finscope.domain.topic;

import com.finscope.domain.article.Article;
import com.finscope.domain.brief.Brief;

import java.util.List;

public class TopicDetail {
    private Topic topic;
    private List<Article> linkedArticles;
    private List<Brief> linkedBriefs;
    private String markdown;

    public TopicDetail(Topic topic, List<Article> linkedArticles, List<Brief> linkedBriefs, String markdown) {
        this.topic = topic;
        this.linkedArticles = linkedArticles;
        this.linkedBriefs = linkedBriefs;
        this.markdown = markdown;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public List<Article> getLinkedArticles() {
        return linkedArticles;
    }

    public void setLinkedArticles(List<Article> linkedArticles) {
        this.linkedArticles = linkedArticles;
    }

    public List<Brief> getLinkedBriefs() {
        return linkedBriefs;
    }

    public void setLinkedBriefs(List<Brief> linkedBriefs) {
        this.linkedBriefs = linkedBriefs;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
