package com.finscope.service.news;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NewsFeedSnapshot {
    private final List<NewsFeedItem> items;
    private final List<String> warnings;
    private final LocalDateTime refreshedAt;
    private final int sourceCount;

    public NewsFeedSnapshot(List<NewsFeedItem> items, List<String> warnings,
                            LocalDateTime refreshedAt, int sourceCount) {
        this.items = Collections.unmodifiableList(new ArrayList<NewsFeedItem>(items));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.refreshedAt = refreshedAt;
        this.sourceCount = sourceCount;
    }

    public List<NewsFeedItem> getItems() { return items; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public int getSourceCount() { return sourceCount; }
}
