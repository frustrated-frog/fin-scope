package com.finscope.domain.source;

import java.time.LocalDateTime;

public class Source {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 名称。
     */
    private String name;
    /**
     * 类型。
     */
    private String type;
    /**
     * 资源 URL。
     */
    private String url;
    /**
     * 是否启用。
     */
    private boolean enabled = true;
    /**
     * 抓取频率分钟数。
     */
    private int fetchFrequencyMinutes = 60;
    /**
     * 是否启用定时抓取。
     */
    private boolean scheduledEnabled = false;
    /**
     * 定时抓取时间列表。
     */
    private String scheduleTimes;
    /**
     * 单次运行最大条目数。
     */
    private int maxItemsPerRun = 10;
    /**
     * 可信度。
     */
    private int credibility = 3;
    /**
     * 标签集合或标签字符串。
     */
    private String tags;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFetchFrequencyMinutes() {
        return fetchFrequencyMinutes;
    }

    public void setFetchFrequencyMinutes(int fetchFrequencyMinutes) {
        this.fetchFrequencyMinutes = fetchFrequencyMinutes;
    }

    public boolean isScheduledEnabled() {
        return scheduledEnabled;
    }

    public void setScheduledEnabled(boolean scheduledEnabled) {
        this.scheduledEnabled = scheduledEnabled;
    }

    public String getScheduleTimes() {
        return scheduleTimes;
    }

    public void setScheduleTimes(String scheduleTimes) {
        this.scheduleTimes = scheduleTimes;
    }

    public int getMaxItemsPerRun() {
        return maxItemsPerRun;
    }

    public void setMaxItemsPerRun(int maxItemsPerRun) {
        this.maxItemsPerRun = maxItemsPerRun;
    }

    public int getCredibility() {
        return credibility;
    }

    public void setCredibility(int credibility) {
        this.credibility = credibility;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
