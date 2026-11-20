package com.finscope.rpc.llm;

/** 单次请求的正文和供应商返回的用量；未提供的计数保留为空。 */
public record ChatCompletionReply(String content, String finishReason, Integer inputTokens,
                                  Integer outputTokens, Integer reasoningTokens, boolean hasReasoning) {
}
