package com.finscope.domain.insight;

public class InsightSection {
    private String title;
    private String content;

    public InsightSection() {
    }

    public InsightSection(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
