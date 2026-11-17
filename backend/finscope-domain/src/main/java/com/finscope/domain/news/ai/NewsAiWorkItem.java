package com.finscope.domain.news.ai;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NewsAiWorkItem {
    private String contentKey;
    private String title;
    private String content;
    private boolean bodyTruncated;
    private LocalDateTime publishedAt;
    private NewsAiAnalysis savedAnalysis;
    private List<NewsAiEvent> candidates = new ArrayList<>();
}
