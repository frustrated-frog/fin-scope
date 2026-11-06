package com.finscope.service.watchlistresearch;

import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.rpc.quote.PythonDailyBarClient;
import com.finscope.service.instrument.StockFundamentalsService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

/** 复用既有行情和财报入口，不在研究用例中引入外部协议。 */
@Service
public class WatchlistResearchInputs {
    @Resource
    private PythonDailyBarClient dailyBars;
    @Resource
    private StockFundamentalsService fundamentals;

    public DailyBarHistory market(String code, String market, boolean refresh) {
        DailyBarHistory history = dailyBars.fetchHistory(code, market, 500, refresh);
        if (!refresh && !history.getBars().isEmpty()
                && history.getBars().stream().anyMatch(bar -> !"QFQ".equals(bar.getAdjustment()))) {
            return dailyBars.fetchHistory(code, market, 500, true);
        }
        return history;
    }

    public StockFundamentals financials(String code, boolean refresh) {
        return fundamentals.loadQuartersForResearch(code, refresh);
    }
}
