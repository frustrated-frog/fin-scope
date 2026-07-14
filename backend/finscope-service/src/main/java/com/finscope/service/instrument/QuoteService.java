package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.service.marketdata.MarketDataGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/** 行情业务门面：所有外部数据访问、缓存和降级统一由 MarketDataGateway 负责。 */
@Service
public class QuoteService {
    private final MarketDataGateway gateway;

    public QuoteService(MarketDataGateway gateway) {
        this.gateway = gateway;
    }

    public List<Quote> fetch(String instrumentType, List<String> codes) {
        return fetch(instrumentType, codes, false);
    }

    public List<Quote> fetch(String instrumentType, List<String> codes, boolean forceRefresh) {
        return gateway.fetchQuotes(instrumentType, codes, forceRefresh).getQuotes();
    }
}
