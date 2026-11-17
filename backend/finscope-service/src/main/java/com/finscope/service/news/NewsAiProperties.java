package com.finscope.service.news;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Data
@Component
@ConfigurationProperties(prefix = "finscope.news-ai")
public class NewsAiProperties {
    private volatile boolean enabled;
    private boolean backfillExisting;
    private int batchSize = 6;
    private int maxBatchesPerRun = 8;
    private int maxCandidates = 6;
    private int contentChars = 1600;
    private int maxOutputTokens = 6000;
    private int maxAttempts = 2;
    private long dailyBudgetMicros = 1_500_000;
    private int dailyMaxCalls = 1500;
    private double inputDollarsPerMillion = 0.10;
    private double outputDollarsPerMillion = 0.50;

    @PostConstruct
    public void validate() {
        if (batchSize < 1 || batchSize > 12 || maxBatchesPerRun < 1 || maxBatchesPerRun > 40
                || maxCandidates < 1 || maxCandidates > 12 || contentChars < 400 || contentChars > 6000
                || maxOutputTokens < 1000 || maxOutputTokens > 16000 || maxAttempts < 1 || maxAttempts > 3
                || dailyBudgetMicros < 0 || dailyMaxCalls < 0 || !Double.isFinite(inputDollarsPerMillion)
                || !Double.isFinite(outputDollarsPerMillion) || inputDollarsPerMillion < 0 || outputDollarsPerMillion < 0) {
            throw new IllegalArgumentException("新闻 AI 批次、候选、预算或计价参数无效");
        }
    }

    public long costMicros(long inputTokens, long outputTokens) {
        return (long) Math.ceil(inputTokens * inputDollarsPerMillion + outputTokens * outputDollarsPerMillion);
    }
}
