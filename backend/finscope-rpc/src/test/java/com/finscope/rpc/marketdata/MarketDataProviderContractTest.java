package com.finscope.rpc.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.quote.FundQuoteAdapter;
import com.finscope.rpc.quote.SinaStockQuoteAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataProviderContractTest {

    @Test
    void providersExposeStableIdentityFailureDomainAndCapabilities() {
        SinaStockQuoteAdapter sina = new SinaStockQuoteAdapter();
        FundQuoteAdapter fund = new FundQuoteAdapter();

        assertEquals("SINA_STOCK", sina.providerCode());
        assertEquals("SINA", sina.providerFamily());
        assertTrue(sina.capabilities().contains(MarketDataCapability.REALTIME_STOCK_QUOTE));
        assertEquals("EASTMONEY_FUND_ESTIMATE", fund.providerCode());
        assertEquals("EASTMONEY", fund.providerFamily());
        assertTrue(fund.capabilities().contains(MarketDataCapability.REALTIME_FUND_ESTIMATE));
    }
}
