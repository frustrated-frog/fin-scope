package com.finscope.rpc.quote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** FundValuationLast 的备用域名 Provider。 */
@Component
public class FundQuoteBackupAdapter extends FundQuoteAdapter {
    private static final String BACKUP_ENDPOINT =
            "https://fundcomapi.eastmoney.com/mm/newCore/FundValuationLast";

    public FundQuoteBackupAdapter() {
        super(BACKUP_ENDPOINT, "EASTMONEY_FUND_VALUATION_BACKUP", 20);
    }

    @Autowired
    public FundQuoteBackupAdapter(QuoteHttpTransport httpTransport) {
        super(BACKUP_ENDPOINT, "EASTMONEY_FUND_VALUATION_BACKUP", 20, httpTransport);
    }

    FundQuoteBackupAdapter(FundDataRequester requester) {
        super(BACKUP_ENDPOINT, "EASTMONEY_FUND_VALUATION_BACKUP", 20, requester);
    }

    FundQuoteBackupAdapter(FundDataRequester requester, Clock clock) {
        super(BACKUP_ENDPOINT, "EASTMONEY_FUND_VALUATION_BACKUP", 20, requester, clock);
    }
}
