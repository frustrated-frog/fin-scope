package com.finscope.rpc.llm;

public interface LlmChatClient {
    boolean isConfigured();

    String modelName();

    String complete(String systemPrompt, String userPrompt) throws Exception;

    default String complete(String systemPrompt, String userPrompt, int timeoutMs) throws Exception {
        return complete(systemPrompt, userPrompt);
    }

    default String complete(String systemPrompt, String userPrompt,
                            int timeoutMs, int maxOutputTokens) throws Exception {
        return complete(systemPrompt, userPrompt, timeoutMs);
    }
}
