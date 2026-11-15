package com.finscope.service.learningcard;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 单个维度的总预算，首次生成与格式修复共用，不延长整个任务的租约。 */
@Data
@Component
@ConfigurationProperties(prefix = "finscope.stock-learning-card")
public class StockLearningCardProperties {
    private int synthesisTimeoutMs = 120_000;
    private int maxOutputTokens = 6_000;
}
