package com.finscope.domain.news;

import com.finscope.common.enums.news.NewsSourceStatus;

/** 单来源本次返回量。缓存回放与实际抓取分别计数。 */
public record NewsFetchCount(String providerCode, NewsSourceStatus status, int fetched, int cached) {
}
