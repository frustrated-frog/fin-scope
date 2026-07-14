package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 腾讯股票与指数 Provider 的共享元数据和批量抓取骨架。 */
abstract class AbstractTencentQuoteAdapter implements QuoteAdapter {
    private final TencentQuoteParser parser;
    private final String providerCode;
    private final String instrumentType;
    private final Set<MarketDataCapability> capabilities;

    AbstractTencentQuoteAdapter(TencentQuoteParser parser, String providerCode,
                                String instrumentType, MarketDataCapability capability) {
        this.parser = parser;
        this.providerCode = providerCode;
        this.instrumentType = instrumentType;
        this.capabilities = Collections.singleton(capability);
    }

    @Override public String providerCode() { return providerCode; }
    @Override public String providerFamily() { return "TENCENT"; }
    @Override public Set<MarketDataCapability> capabilities() { return capabilities; }
    @Override public int priority() { return 10; }
    @Override public int batchLimit() { return 80; }
    @Override public Duration minimumInterval() { return Duration.ofMillis(100); }
    @Override public Duration timeout() { return Duration.ofSeconds(8); }
    @Override public boolean supports(String type) { return instrumentType.equalsIgnoreCase(type); }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        return parser.fetch(codes.stream().map(this::toSymbol).collect(Collectors.toList()));
    }

    protected abstract String toSymbol(String code);

    protected String normalized(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("quote code is required");
        }
        return code.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
