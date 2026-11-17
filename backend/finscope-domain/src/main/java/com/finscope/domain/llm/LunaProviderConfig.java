package com.finscope.domain.llm;

import lombok.Data;
import lombok.ToString;

@Data
public class LunaProviderConfig {
    private String baseUrl = "https://api.openai.com/v1";
    @ToString.Exclude
    private String apiKey = "";
    private String model = "gpt-6-luna";
    private int timeoutMs = 45000;
}
