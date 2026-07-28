package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketdata.ProviderResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 行情适配器：把外部行情源还原为统一的 Quote。
 * 与 SourceAdapter 平级，隔离在 rpc 层，实现可切换。
 */
public interface QuoteAdapter extends MarketDataProvider {

    /** 是否支持该标的类型：STOCK | FUND | SECTOR */
    boolean supports(String instrumentType);

    /**
     * 批量拉取行情。
     * @param codes 标的代码列表（如 600519、000001、BK0477）
     */
    List<Quote> fetch(List<String> codes) throws Exception;

    default ProviderResult<List<Quote>> fetchResult(List<String> codes) throws Exception {
        List<Quote> data = fetch(codes);
        return ProviderResult.of(data, LocalDateTime.now(), ProviderResult.hashOf(data),
                Collections.<String>emptyList());
    }
}
