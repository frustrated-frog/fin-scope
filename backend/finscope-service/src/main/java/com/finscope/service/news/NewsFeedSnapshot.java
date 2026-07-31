package com.finscope.service.news;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NewsFeedSnapshot {
    private final List<NewsFeedItem> items;
    private final List<String> warnings;
    private final LocalDateTime refreshedAt;
    private final int sourceCount;
    private final Map<String, Integer> categoryCounts;
    private final int unclassifiedCount;

    public NewsFeedSnapshot(List<NewsFeedItem> items, List<String> warnings,
                            LocalDateTime refreshedAt, int sourceCount) {
        this(items, warnings, refreshedAt, sourceCount, Collections.emptyMap(), 0);
    }

    public NewsFeedSnapshot(List<NewsFeedItem> items, List<String> warnings,
                            LocalDateTime refreshedAt, int sourceCount,
                            Map<String, Integer> categoryCounts, int unclassifiedCount) {
        this.items = Collections.unmodifiableList(new ArrayList<NewsFeedItem>(items));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.refreshedAt = refreshedAt;
        this.sourceCount = sourceCount;
        this.categoryCounts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(categoryCounts));
        this.unclassifiedCount = unclassifiedCount;
    }

    public List<NewsFeedItem> getItems() { return items; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public int getSourceCount() { return sourceCount; }
    public Map<String, Integer> getCategoryCounts() { return categoryCounts; }
    public int getUnclassifiedCount() { return unclassifiedCount; }
}
