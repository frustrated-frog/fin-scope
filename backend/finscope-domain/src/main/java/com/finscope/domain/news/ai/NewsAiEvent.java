package com.finscope.domain.news.ai;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NewsAiEvent {
    private String eventKey;
    private int revision;
    private NewsAiAnalysis analysis;
    private LocalDateTime firstAt;
    private LocalDateTime lastAt;
}
