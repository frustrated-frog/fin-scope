package com.finscope.domain.news.ai;

import com.finscope.common.enums.news.NewsAiCallState;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 有界只读调用审计，不含原文、请求体、密钥或供应商错误正文。 */
@Data
public class NewsAiCallAudit {
    private String callId;
    private String createdAt;
    private String model;
    private NewsAiCallState state;
    private String protocol;
    private Long inputTokens;
    private Long outputTokens;
    private Long actualMicros;
    private long reservedMicros;
    private Long requestBytes;
    private Integer candidateReferences;
    private Integer uniqueCandidateCards;
    private String errorType;
    private boolean cancelledBeforeSend;
    private List<NewsAiCallItemAudit> items = new ArrayList<>();
}
