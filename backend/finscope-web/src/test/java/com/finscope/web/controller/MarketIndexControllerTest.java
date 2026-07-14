package com.finscope.web.controller;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.instrument.MarketIndexService;
import com.finscope.service.instrument.MarketIndexView;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketIndexController.class)
@Import(FinScopeProperties.class)
class MarketIndexControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketIndexService marketIndexService;

    @Test
    void getsMarketIndices() throws Exception {
        Quote quote = new Quote();
        quote.setPrice(3200.00);
        quote.setChangeAmount(12.50);
        quote.setChangePct(0.39);
        quote.setValid(true);
        quote.setQualityStatus(MarketDataQualityStatus.STALE_FALLBACK);
        quote.setSourceCode("TENCENT_INDEX");
        quote.setAsOf(LocalDateTime.of(2026, 7, 14, 9, 57));
        quote.setRetrievedAt(LocalDateTime.of(2026, 7, 14, 9, 57, 5));
        quote.setStaleAgeSeconds(180L);
        quote.setWarning("正在显示 3 分钟前的数据");
        quote.setRefreshId("r-1");
        when(marketIndexService.list()).thenReturn(Collections.singletonList(
                new MarketIndexView("000001", "上证指数", quote)
        ));

        mockMvc.perform(get("/api/market-indices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("000001"))
                .andExpect(jsonPath("$[0].name").value("上证指数"))
                .andExpect(jsonPath("$[0].quoteValid").value(true))
                .andExpect(jsonPath("$[0].qualityStatus").value("STALE_FALLBACK"))
                .andExpect(jsonPath("$[0].sourceCode").value("TENCENT_INDEX"))
                .andExpect(jsonPath("$[0].staleAgeSeconds").value(180))
                .andExpect(jsonPath("$[0].warning").value("正在显示 3 分钟前的数据"))
                .andExpect(jsonPath("$[0].refreshId").value("r-1"));
    }
}
