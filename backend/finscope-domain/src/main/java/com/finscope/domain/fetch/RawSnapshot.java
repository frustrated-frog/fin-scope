package com.finscope.domain.fetch;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RawSnapshot {
    private Long id;
    private Long fetchRunId;
    private Long sourceId;
    private String purpose;
    private String method;
    private String requestUrl;
    private String finalUrl;
    private String requestHeadersJson;
    private String status;
    private Integer httpStatus;
    private String errorType;
    private String errorMessage;
    private String contentType;
    private String charsetName;
    private int bodyBytes;
    private String bodySha256;
    private String bodyPath;
    private int attemptCount;
    private long durationMs;
    private String policyVersion;
    private String parserVersion;
    private LocalDateTime fetchedAt;
    private LocalDateTime parsedAt;
}
