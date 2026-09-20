package com.finscope.domain.news;

import com.finscope.common.enums.news.NewsSourceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NewsSourceHealth {
    private String providerCode;
    private NewsSourceStatus status;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime lastSuccessAt;
}
