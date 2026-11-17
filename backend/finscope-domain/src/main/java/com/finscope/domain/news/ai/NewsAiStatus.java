package com.finscope.domain.news.ai;

import lombok.Data;
import java.util.Map;

/** 只包含运行状态与用量，禁止携带供应商凭据。费用单位为百万分之一美元。 */
@Data
public class NewsAiStatus {
    private boolean enabled;
    private boolean configured;
    private String model;
    private long dailyBudgetMicros;
    private int dailyMaxCalls;
    private long calls;
    private long inputTokens;
    private long outputTokens;
    private long actualMicros;
    private long unresolvedMicros;
    private long remainingMicros;
    private Map<String, Long> jobs;
}
