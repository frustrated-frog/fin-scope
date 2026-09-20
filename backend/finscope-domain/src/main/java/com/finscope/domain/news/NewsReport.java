package com.finscope.domain.news;

import lombok.Data;
import java.time.LocalDateTime;

/** 原始报道身份不随正文、分类或阅读状态变化。 */
@Data
public class NewsReport {
    private String id;
    private long arrivalSequence;
    private String providerCode;
    private String sourceName;
    private String sourceTier;
    private String kind;
    private String title;
    private String content;
    private String url;
    private LocalDateTime publishedAt;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private int contentVersion;
    private int readVersion;
    private String categoryCode;
    private String categoryName;
    private String classificationReason;
    private String ruleVersion;
    private boolean manuallyReviewed;
    private String manualReason;

    public boolean isUnread() {
        return readVersion < contentVersion;
    }

    public boolean isHistoricalBackfill() {
        return publishedAt != null && firstSeenAt != null
                && publishedAt.toLocalDate().isBefore(firstSeenAt.toLocalDate());
    }
}
