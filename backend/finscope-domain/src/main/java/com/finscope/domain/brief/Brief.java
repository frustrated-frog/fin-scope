package com.finscope.domain.brief;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Brief {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 简报日期。
     */
    private LocalDate briefDate;
    /**
     * 标题。
     */
    private String title;
    /**
     * 正文内容。
     */
    private String content;
    /**
     * Markdown 文件路径。
     */
    private String markdownPath;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getBriefDate() {
        return briefDate;
    }

    public void setBriefDate(LocalDate briefDate) {
        this.briefDate = briefDate;
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

    public String getMarkdownPath() {
        return markdownPath;
    }

    public void setMarkdownPath(String markdownPath) {
        this.markdownPath = markdownPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
