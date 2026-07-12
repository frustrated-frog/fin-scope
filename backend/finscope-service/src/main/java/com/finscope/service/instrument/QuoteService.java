package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.quote.QuoteAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情服务：按标的类型路由到合适的 QuoteAdapter，抓取失败时兜底为无效行情，不阻断面板。
 */
@Service
@Slf4j
public class QuoteService {
    private static final long CACHE_TTL_MS = 30_000L;

    @Resource
    private List<QuoteAdapter> adapters;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<String, CachedQuote>();

    /**
     * 拉取一批同类型标的的行情。
     * @param instrumentType STOCK | FUND | SECTOR
     */
    public List<Quote> fetch(String instrumentType, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new ArrayList<>();
        }
        QuoteAdapter adapter = resolve(instrumentType);
        if (adapter == null) {
            log.warn("未找到行情适配器 instrumentType={}", instrumentType);
            return fallback(codes, "暂不支持该类型行情");
        }
        Map<String, Quote> quotesByCode = new LinkedHashMap<String, Quote>();
        List<String> missingCodes = new ArrayList<String>();
        long now = System.currentTimeMillis();
        for (String code : codes) {
            String key = cacheKey(instrumentType, code);
            CachedQuote cached = cache.get(key);
            if (cached != null && cached.expiresAt > now) {
                quotesByCode.put(code, cached.quote);
            } else {
                cache.remove(key);
                missingCodes.add(code);
            }
        }
        if (missingCodes.isEmpty()) {
            return orderedQuotes(codes, quotesByCode);
        }
        try {
            List<Quote> fetched = adapter.fetch(missingCodes);
            for (Quote quote : fetched) {
                if (quote != null && quote.getInstrumentCode() != null) {
                    quotesByCode.put(quote.getInstrumentCode(), quote);
                    cache.put(cacheKey(instrumentType, quote.getInstrumentCode()), new CachedQuote(quote, now + CACHE_TTL_MS));
                }
            }
            for (String code : missingCodes) {
                if (!quotesByCode.containsKey(code)) {
                    Quote fallback = fallback(java.util.Collections.singletonList(code), "行情源未返回该标的").get(0);
                    quotesByCode.put(code, fallback);
                    cache.put(cacheKey(instrumentType, code), new CachedQuote(fallback, now + CACHE_TTL_MS));
                }
            }
            return orderedQuotes(codes, quotesByCode);
        } catch (Exception ex) {
            log.warn("行情抓取失败 instrumentType={} codes={} message={}", instrumentType, codes, ex.getMessage());
            List<Quote> fallbacks = fallback(missingCodes, "行情抓取失败");
            for (Quote fallback : fallbacks) {
                quotesByCode.put(fallback.getInstrumentCode(), fallback);
                cache.put(cacheKey(instrumentType, fallback.getInstrumentCode()), new CachedQuote(fallback, now + CACHE_TTL_MS));
            }
            return orderedQuotes(codes, quotesByCode);
        }
    }

    private QuoteAdapter resolve(String instrumentType) {
        for (QuoteAdapter adapter : adapters) {
            if (adapter.supports(instrumentType)) {
                return adapter;
            }
        }
        return null;
    }

    private List<Quote> fallback(List<String> codes, String note) {
        List<Quote> quotes = new ArrayList<>();
        for (String code : codes) {
            Quote quote = new Quote();
            quote.setInstrumentCode(code);
            quote.setValid(false);
            quote.setNote(note);
            quotes.add(quote);
        }
        return quotes;
    }

    private List<Quote> orderedQuotes(List<String> codes, Map<String, Quote> quotesByCode) {
        List<Quote> quotes = new ArrayList<Quote>();
        for (String code : codes) {
            Quote quote = quotesByCode.get(code);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    private String cacheKey(String instrumentType, String code) {
        return String.valueOf(instrumentType).toUpperCase(Locale.ROOT) + ":" + code;
    }

    private static class CachedQuote {
        private final Quote quote;
        private final long expiresAt;

        private CachedQuote(Quote quote, long expiresAt) {
            this.quote = quote;
            this.expiresAt = expiresAt;
        }
    }
}
