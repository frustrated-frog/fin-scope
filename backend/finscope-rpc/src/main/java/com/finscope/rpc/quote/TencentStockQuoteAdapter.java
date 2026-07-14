package com.finscope.rpc.quote;

import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

/** 腾讯 A 股股票行情 Provider。 */
@Component
public class TencentStockQuoteAdapter extends AbstractTencentQuoteAdapter {
    public TencentStockQuoteAdapter(TencentQuoteParser parser) {
        super(parser, "TENCENT_STOCK", "STOCK", MarketDataCapability.REALTIME_STOCK_QUOTE);
    }

    @Override
    protected String toSymbol(String code) {
        String value = normalized(code);
        if (value.startsWith("sh") || value.startsWith("sz") || value.startsWith("bj")) return value;
        if (value.startsWith("6")) return "sh" + value;
        if (value.startsWith("0") || value.startsWith("3")) return "sz" + value;
        if (value.startsWith("4") || value.startsWith("8")) return "bj" + value;
        throw new IllegalArgumentException("unsupported A-share code: " + code);
    }
}
