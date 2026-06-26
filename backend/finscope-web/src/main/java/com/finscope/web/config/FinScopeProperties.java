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
        private boolean enabled = true;
        private String baseUrl = "https://api.letmyai.com/v1";
        private String apiKey = "sk-letmyai-08-c8c2d4b34676ecbe6ca375aecf5550e9229cc00e6980df7a";
        private String model = "gpt-5.5";
        private int timeoutMs = 30000;
        private double temperature = 0.2;
    }
}
