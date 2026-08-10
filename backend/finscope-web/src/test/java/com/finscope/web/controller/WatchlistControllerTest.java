package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.instrument.FundHoldingDetail;
import com.finscope.service.instrument.FundHoldingDetailService;
import com.finscope.service.instrument.FundHoldingPositionView;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    @MockBean
    private FundHoldingDetailService fundHoldingDetailService;

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
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].qualityStatus").value("FRESH_FALLBACK"))
                .andExpect(jsonPath("$.data[0].sourceCode").value("SINA_STOCK"))
                .andExpect(jsonPath("$.data[0].warning").value("腾讯行情暂不可用，已自动切换至新浪。"))
                .andExpect(jsonPath("$.data[0].refreshId").value("r-watchlist"));

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

    @Test
    void returnsAggregatedFundHoldingDetailWithNullableContributions() throws Exception {
        FundHoldingPositionView fresh = new FundHoldingPositionView(
                1, "688012", "中微公司", 8.0d, 4.2d, 1967.7d,
                468.5d, 2.0d, 0.16d, true,
                LocalDateTime.of(2026, 8, 10, 14, 29, 58),
                MarketDataQualityStatus.FRESH_PRIMARY, null);
        FundHoldingPositionView unavailable = new FundHoldingPositionView(
                2, "688120", "华海清科", 4.0d, 2.24d, 721.68d,
                null, null, null, false, null,
                MarketDataQualityStatus.UNAVAILABLE, "实时行情不可用");
        FundHoldingDetail detail = new FundHoldingDetail(
                "021894", "易方达半导体设备ETF联接C",
                LocalDate.of(2026, 6, 30), LocalDateTime.of(2026, 8, 10, 14, 30),
                LocalDateTime.of(2026, 8, 10, 14, 29, 58),
                LocalDateTime.of(2026, 8, 10, 14, 30),
                "TENCENT_STOCK", MarketDataQualityStatus.PARTIAL_FRESH,
                "部分行情不可用", "refresh-1", 12.0d, 0.16d,
                1, 2, false, "按最近披露持仓估算",
                java.util.Arrays.asList(fresh, unavailable));
        when(fundHoldingDetailService.load("021894", true)).thenReturn(detail);

        mockMvc.perform(get("/api/watchlist/021894/fund-holdings")
                        .param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundCode").value("021894"))
                .andExpect(jsonPath("$.data.disclosureDate").value("2026-06-30"))
                .andExpect(jsonPath("$.data.quoteQualityStatus").value("PARTIAL_FRESH"))
                .andExpect(jsonPath("$.data.estimatedHoldingCount").value(1))
                .andExpect(jsonPath("$.data.holdings[0].estimatedContributionPct").value(0.16))
                .andExpect(jsonPath("$.data.holdings[1].estimatedContributionPct").isEmpty());

        verify(fundHoldingDetailService).load("021894", true);
    }
}
