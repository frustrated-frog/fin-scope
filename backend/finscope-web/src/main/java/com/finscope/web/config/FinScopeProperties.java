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
    private SearchProperties search = new SearchProperties();
    private MarketIntelProperties marketIntel = new MarketIntelProperties();

    @Data
    public static class LlmProperties {
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private int timeoutMs = 30000;
        private double temperature = 0.2;
    }

    @Data
    public static class SearchProperties {
        private boolean enabled = false;
        private String provider = "tavily";
        private String apiKey = "";
        private AnySearchProperties anySearch = new AnySearchProperties();
        private SearchFusionProperties fusion = new SearchFusionProperties();
    }

    @Data
    public static class AnySearchProperties {
        private boolean enabled = false;
        private String apiKey = "";
        private String baseUrl = "https://api.anysearch.com";
        private int timeoutMs = 15000;
        private int maxResponseBytes = 2097152;
    }

    @Data
    public static class SearchFusionProperties {
        private int rrfConstant = 60;
        private int maxPerDomain = 2;
        private int concurrency = 2;
    }

    @Data
    public static class MarketIntelProperties {
        private boolean enabled = true;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
        private int maxResponseBytes = 2097152;
        private int eastmoneyMinIntervalMs = 1000;
        private String ruleVersion = "capital-rules-v1";
        private boolean agentEnabled = true;
        private int agentTimeoutMs = 15000;
        private String promptVersion = "capital-interpret-v3";
    }
}
