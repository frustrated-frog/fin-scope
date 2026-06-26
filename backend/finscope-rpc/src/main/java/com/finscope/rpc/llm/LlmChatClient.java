package com.finscope.rpc.llm;

public interface LlmChatClient {
    boolean isConfigured();

    String modelName();

    String complete(String systemPrompt, String userPrompt) throws Exception;
}
