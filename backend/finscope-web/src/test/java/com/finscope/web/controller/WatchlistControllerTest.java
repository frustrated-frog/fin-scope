package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.service.instrument.WatchlistService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import(FinScopeProperties.class)
class WatchlistControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private WatchlistService watchlistService;

    @Test
    void listsOnlyInvestmentItemsThroughTypedService() throws Exception {
        WatchlistItem item = new WatchlistItem();
        item.setId(1L);
        item.setCode("600519");
        item.setType("STOCK");
        item.setName("贵州茅台");
        Quote quote = new Quote();
        quote.setInstrumentCode("600519");
        quote.setPrice(1500.0);
        quote.setValid(true);
        quote.setQualityStatus(MarketDataQualityStatus.FRESH_FALLBACK);
        quote.setSourceCode("SINA_STOCK");
        quote.setWarning("腾讯行情暂不可用，已自动切换至新浪。");
        quote.setRefreshId("r-watchlist");
        when(watchlistService.listInvestmentItemsWithQuotes(false)).thenReturn(
                Collections.singletonList(new WatchlistItemView(item, quote, null)));

        mockMvc.perform(get("/api/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].qualityStatus").value("FRESH_FALLBACK"))
                .andExpect(jsonPath("$[0].sourceCode").value("SINA_STOCK"))
                .andExpect(jsonPath("$[0].warning").value("腾讯行情暂不可用，已自动切换至新浪。"))
                .andExpect(jsonPath("$[0].refreshId").value("r-watchlist"));

        verify(watchlistService).listInvestmentItemsWithQuotes(false);
    }

    @Test
    void rejectsSectorOnOrdinaryWatchlistEndpoint() throws Exception {
        doThrow(new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "板块请使用板块关注接口"))
                .when(watchlistService).addInvestment("BK1036", "SECTOR", null);

        mockMvc.perform(post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BK1036\",\"type\":\"SECTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("板块请使用板块关注接口"));
    }
}
