package com.finscope.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "finscope")
@Data
public class FinScopeProperties {
    private String dataRoot = "../data";
    private String corsOrigin = "http://localhost:*";
    private LlmProperties llm = new LlmProperties();

    @Data
    public static class LlmProperties {
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private int timeoutMs = 30000;
        private double temperature = 0.2;
    }
}
