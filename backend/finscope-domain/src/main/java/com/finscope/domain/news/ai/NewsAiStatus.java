package com.finscope.domain.news.ai;

import lombok.Data;
import java.util.Map;

/** 只包含运行状态与用量，禁止携带供应商凭据。费用单位为百万分之一美元。 */
@Data
public class NewsAiStatus {
    private boolean enabled;
    private boolean configured;
    private String model;
    // null表示未设置每日上限，剩余额度同样为null。
    private Long dailyBudgetMicros;
    private Integer dailyMaxCalls;
    private long calls;
    private long inputTokens;
    private long outputTokens;
    private long actualMicros;
    private long unresolvedMicros;
    private Long remainingMicros;
    private Map<String, Long> jobs;
}
