package com.finscope.domain.news;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NewsReportVersion {
    private String reportId;
    private int version;
    private String title;
    private String content;
    private LocalDateTime detectedAt;
}
