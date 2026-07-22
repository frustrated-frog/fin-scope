package com.finscope.rpc.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.quote.FundNavHistoryAdapter;
import com.finscope.rpc.quote.FundQuoteBackupAdapter;
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
        FundQuoteBackupAdapter fundBackup = new FundQuoteBackupAdapter();
        FundNavHistoryAdapter fundNav = new FundNavHistoryAdapter();

        assertEquals("SINA_STOCK", sina.providerCode());
        assertEquals("SINA", sina.providerFamily());
        assertTrue(sina.capabilities().contains(MarketDataCapability.REALTIME_STOCK_QUOTE));
        assertEquals("EASTMONEY_FUND_VALUATION", fund.providerCode());
        assertEquals("EASTMONEY", fund.providerFamily());
        assertTrue(fund.capabilities().contains(MarketDataCapability.REALTIME_FUND_ESTIMATE));
        assertEquals(10, fund.priority());
        assertEquals("EASTMONEY_FUND_VALUATION_BACKUP", fundBackup.providerCode());
        assertEquals("EASTMONEY", fundBackup.providerFamily());
        assertEquals(20, fundBackup.priority());
        assertTrue(fundBackup.capabilities().contains(MarketDataCapability.REALTIME_FUND_ESTIMATE));
        assertEquals("EASTMONEY_FUND_CONFIRMED_NAV", fundNav.providerCode());
        assertEquals(30, fundNav.priority());
    }
}
