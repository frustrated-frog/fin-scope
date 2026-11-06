package com.finscope.web.controller;

import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.StockMarketHistory;
import com.finscope.service.instrument.StockMarketHistoryService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistStockDisplayController.class)
@Import(FinScopeProperties.class)
class WatchlistStockDisplayControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private StockMarketHistoryService service;

    @Test
    void serializesHistoryMetadataAndCompletionWithoutLosingPricePrecision() throws Exception {
        DailyBarPoint bar = new DailyBarPoint();
        bar.setTradeDate(LocalDate.of(2026, 1, 5));
        bar.setClose(new BigDecimal("100.12"));
        bar.setAdjustment("QFQ");
        bar.setIsComplete(true);
        DailyBarHistory stock = new DailyBarHistory();
        stock.setCode("600519");
        stock.setSourceCode("TENCENT_DAILY");
        stock.setQualityStatus("FRESH_PRIMARY");
        stock.setBars(Collections.singletonList(bar));
        StockMarketHistory result = new StockMarketHistory();
        result.setStock(stock);
        result.setBenchmarkName("沪深300");
        result.setBenchmarkCode("000300.SH");
        result.getWarnings().add("基准暂不可用");
        when(service.load("600519", true)).thenReturn(result);

        mvc.perform(get("/api/watchlist/stocks/600519/market-history?refresh=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock.code").value("600519"))
                .andExpect(jsonPath("$.data.stock.sourceCode").value("TENCENT_DAILY"))
                .andExpect(jsonPath("$.data.stock.bars[0].tradeDate").value("2026-01-05"))
                .andExpect(jsonPath("$.data.stock.bars[0].close").value(100.12))
                .andExpect(jsonPath("$.data.stock.bars[0].adjustment").value("QFQ"))
                .andExpect(jsonPath("$.data.stock.bars[0].isComplete").value(true))
                .andExpect(jsonPath("$.data.benchmarkCode").value("000300.SH"))
                .andExpect(jsonPath("$.data.warnings[0]").value("基准暂不可用"));
    }
}
