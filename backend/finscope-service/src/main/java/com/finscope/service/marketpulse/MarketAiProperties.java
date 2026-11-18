package com.finscope.service.marketpulse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Data
@Component
@ConfigurationProperties(prefix = "finscope.market-ai")
public class MarketAiProperties {
    private boolean enabled = true;
    private String reasoningEffort = "low";
    private int maxOutputTokens = 7000;
    private int maxScoreAdjustment = 15;

    @PostConstruct
    public void validate() {
        if (!java.util.Set.of("none", "low", "medium", "high", "xhigh", "max").contains(reasoningEffort)
                || maxScoreAdjustment < 0 || maxScoreAdjustment > 20
                || maxOutputTokens < 1500 || maxOutputTokens > 12000) {
            throw new IllegalArgumentException("市场研判推理强度或输出长度无效");
        }
    }
}
