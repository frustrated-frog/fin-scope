package com.finscope.rpc.llm;

import org.springframework.stereotype.Component;

/** 保留既有Bean与调用兼容入口，共用Luna Responses传输。 */
@Component
public class NewsAiLunaClient extends LunaResponsesClient {
}
