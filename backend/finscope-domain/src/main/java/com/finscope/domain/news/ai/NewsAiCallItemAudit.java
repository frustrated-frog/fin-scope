package com.finscope.domain.news.ai;

import com.finscope.common.enums.news.NewsAiInputMode;
import lombok.Data;

@Data
public class NewsAiCallItemAudit {
    private String reportId;
    private String occurrenceKey;
    private NewsAiInputMode mode;
    private int attempt;
    private int sourceChars;
    private int sentChars;
}
