package com.finscope.domain.news.ai;

import lombok.Data;

/** 日内累计检查次数及实际发送量；检查次数不是独立新闻数。 */
@Data
public class NewsAiEfficiency {
    private long eligibleChecks;
    private long bindingReuseChecks;
    private long guardedChecks;
    private long processingMs;
    private long itemsSent;
    private long fullTextItems;
    private long matchingOnlyItems;
    private long reclassificationItems;
    private long retryItems;
    private long sourceChars;
    private long sentChars;
    private long candidateReferences;
    private long uniqueCandidateCards;
    private long requestBytes;
}
