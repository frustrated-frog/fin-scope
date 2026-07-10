package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.quote.QuoteAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 行情服务：按标的类型路由到合适的 QuoteAdapter，抓取失败时兜底为无效行情，不阻断面板。
 */
@Service
@Slf4j
public class QuoteService {
    @Resource
    private List<QuoteAdapter> adapters;

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
        try {
            return adapter.fetch(codes);
        } catch (Exception ex) {
            log.warn("行情抓取失败 instrumentType={} codes={} message={}", instrumentType, codes, ex.getMessage());
            return fallback(codes, "行情抓取失败");
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
}