package com.finscope.domain.news.ai;

import com.finscope.common.enums.news.NewsAiInputMode;
import lombok.Data;

/** 调用元数据，不保存原文或密钥。 */
@Data
public class NewsAiDispatchItem {
    private String jobKey;
    private String owner;
    private String occurrenceKey;
    private NewsAiInputMode mode;
    private int sourceChars;
    private int sentChars;
}
