package com.finscope.rpc.marketintel.tencent;

import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/** 显式启用的本机网络验收，默认测试不依赖外部站点。 */
@EnabledIfSystemProperty(named = "finscope.liveReplay", matches = "true")
class TencentCapitalReplayLiveTest {
    @Test
    void readsIndependentMinuteEvidenceForThreeLocalWatchlistStocks(TestReporter reporter) {
        TencentCapitalReplayProvider provider = TencentCapitalReplayProviderTest.provider(new JdkFinanceHttpClient());
        for (String code : new String[]{"002185", "605058", "603092"}) {
            Instrument stock = TencentCapitalReplayProviderTest.stock();
            stock.setCode(code);
            stock.setMarket(code.startsWith("6") ? "SH" : "SZ");
            CapitalReplayData result = provider.fetchReplay(stock);
            long amountCount = result.getPrices().stream().filter(point -> point.getIntervalTradeAmount() != null).count();
            assertTrue(result.getPrices().size() >= 200, code + " should have actual minute history");
            assertTrue(amountCount >= 200, code + " should have actual interval turnover");
            assertTrue(result.getFlows().isEmpty());
            reporter.publishEntry(code, "priceMinutes=" + result.getPrices().size() + ",amountMinutes=" + amountCount);
        }
    }
}
