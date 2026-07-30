package com.finscope.rpc.search;

import com.finscope.domain.search.SearchResult;

import java.util.List;

/**
 * 联网搜索出口，隔离具体搜索服务商（Tavily/Serper 等）。
 * 与 LlmChatClient 平级，实现可切换；未配置时 isConfigured 返回 false，上层走兜底。
 */
public interface WebSearchClient extends WebSearchProvider {

    @Override
    default String providerCode() {
        return "LEGACY";
    }

    boolean isConfigured();

    /**
     * 执行一次联网搜索。
     * @param query 查询词
     * @param maxResults 最大结果数
     */
    List<SearchResult> search(String query, int maxResults) throws Exception;

    @Override
    default List<SearchResult> search(com.finscope.domain.search.WebSearchRequest request) throws Exception {
        return search(request.getQuery(), request.getMaxResults());
    }
}
