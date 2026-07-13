package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 提供固定的 A 股市场基准行情；不属于用户自选。 */
@Service
public class MarketIndexService {
    private static final List<IndexDefinition> INDICES = Arrays.asList(
            new IndexDefinition("000001", "上证指数"),
            new IndexDefinition("399001", "深证成指"),
            new IndexDefinition("399006", "创业板指"),
            new IndexDefinition("000688", "科创50")
    );

    @Resource
    private QuoteService quoteService;

    public List<MarketIndexView> list() {
        return list(false);
    }

    public List<MarketIndexView> list(boolean forceRefresh) {
        List<String> codes = new ArrayList<String>();
        for (IndexDefinition index : INDICES) {
            codes.add(index.code);
        }
        Map<String, Quote> quoteByCode = new LinkedHashMap<String, Quote>();
        for (Quote quote : quoteService.fetch("INDEX", codes, forceRefresh)) {
            if (quote != null && quote.getInstrumentCode() != null) {
                quoteByCode.put(quote.getInstrumentCode(), quote);
            }
        }
        List<MarketIndexView> views = new ArrayList<MarketIndexView>();
        for (IndexDefinition index : INDICES) {
            Quote quote = quoteByCode.get(index.code);
            views.add(new MarketIndexView(index.code, index.name, quote == null ? unavailable(index.code) : quote));
        }
        return views;
    }

    private Quote unavailable(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setValid(false);
        quote.setNote("暂无行情");
        return quote;
    }

    private static class IndexDefinition {
        private final String code;
        private final String name;

        private IndexDefinition(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
