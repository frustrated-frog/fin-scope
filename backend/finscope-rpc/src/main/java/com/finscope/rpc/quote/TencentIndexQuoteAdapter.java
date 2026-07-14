package com.finscope.rpc.quote;

import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

/** 腾讯境内主要指数行情 Provider，与股票代码空间隔离。 */
@Component
public class TencentIndexQuoteAdapter extends AbstractTencentQuoteAdapter {
    public TencentIndexQuoteAdapter(TencentQuoteParser parser) {
        super(parser, "TENCENT_INDEX", "INDEX", MarketDataCapability.REALTIME_INDEX_QUOTE);
    }

    @Override
    protected String toSymbol(String code) {
        String value = normalized(code);
        if (value.startsWith("sh") || value.startsWith("sz")) return value;
        if (value.startsWith("399")) return "sz" + value;
        if (value.startsWith("000")) return "sh" + value;
        throw new IllegalArgumentException("unsupported mainland index code: " + code);
    }
}
