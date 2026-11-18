package com.finscope.domain.marketpulse.ai;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 仅用于本地诊断和离线重放；不进入页面响应或应用日志。 */
@Data
public class MarketAiArtifact {
    private String cacheKey;
    private String owner;
    @ToString.Exclude
    private MarketAiInput input;
    @ToString.Exclude
    private String responseText;
    private boolean responseCompleted;
    private boolean responseTruncated;
    private LocalDateTime respondedAt;
    private Long inputTokens;
    private Long outputTokens;
    private MarketAiFailure failure;
}
