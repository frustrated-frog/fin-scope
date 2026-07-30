package com.finscope.rpc.search;

import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;

import java.util.List;

/**
 * 单一联网搜索供应商合同。
 */
public interface WebSearchProvider {
    String providerCode();
    boolean isConfigured();
    List<SearchResult> search(WebSearchRequest request) throws Exception;
}
