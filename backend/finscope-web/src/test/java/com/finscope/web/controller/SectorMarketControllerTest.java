package com.finscope.web.controller;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.instrument.SectorMarketOverview;
import com.finscope.service.instrument.SectorMarketSearchResult;
import com.finscope.service.instrument.SectorMarketService;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.service.instrument.WatchlistService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SectorMarketController.class)
@Import(FinScopeProperties.class)
class SectorMarketControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SectorMarketService sectorMarketService;
    @MockBean
    private WatchlistService watchlistService;

    @Test
    void exposesOverviewQualityAndRankings() throws Exception {
        SectorMarketEntry entry = entry();
        when(sectorMarketService.overview(SectorCategory.INDUSTRY, 5, false)).thenReturn(
                new SectorMarketOverview(SectorCategory.INDUSTRY, MarketDataQualityStatus.FRESH_PRIMARY,
                        "PYTHON_TONGHUASHUN_SECTOR", LocalDateTime.of(2026, 7, 14, 9, 59),
                        LocalDateTime.of(2026, 7, 14, 10, 0), null, null, "refresh-overview",
                        Collections.singletonList(entry), Collections.<SectorMarketEntry>emptyList()));

        mockMvc.perform(get("/api/sector-market/overview")
                        .param("category", "INDUSTRY").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualityStatus").value("FRESH_PRIMARY"))
                .andExpect(jsonPath("$.data.sourceCode").value("PYTHON_TONGHUASHUN_SECTOR"))
                .andExpect(jsonPath("$.data.refreshId").value("refresh-overview"))
                .andExpect(jsonPath("$.data.leaders[0].code").value("881121"))
                .andExpect(jsonPath("$.data.leaders[0].sourceRank").value(1))
                .andExpect(jsonPath("$.data.leaders[0].mainNetInflow").value(1200000000D))
                .andExpect(jsonPath("$.data.leaders[0].leaderStockName").value("中芯国际"));
    }

    @Test
    void searchesAcrossAllCategoriesWhenCategoryIsAll() throws Exception {
        when(sectorMarketService.search("半导体", null, 10)).thenReturn(
                new SectorMarketSearchResult(MarketDataQualityStatus.FRESH_FALLBACK,
                        "BACKUP_SECTOR", LocalDateTime.of(2026, 7, 14, 9, 59),
                        LocalDateTime.of(2026, 7, 14, 10, 0), null,
                        "已自动切换备用数据源", "refresh-search", Collections.singletonList(entry())));

        mockMvc.perform(get("/api/sector-market/search")
                        .param("q", "半导体").param("category", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualityStatus").value("FRESH_FALLBACK"))
                .andExpect(jsonPath("$.data.warning").value("已自动切换备用数据源"))
                .andExpect(jsonPath("$.data.items[0].category").value("INDUSTRY"));
    }

    @Test
    void followsAndUnfollowsSectorIdempotently() throws Exception {
        WatchlistItem item = sectorItem();
        Quote quote = new Quote();
        quote.setInstrumentCode("881121");
        quote.setChangePct(2.6);
        quote.setMainNetInflow(1_200_000_000D);
        quote.setValid(true);
        quote.setQualityStatus(MarketDataQualityStatus.FRESH_FALLBACK);
        quote.setSourceCode("PYTHON_TONGHUASHUN_SECTOR");
        quote.setWarning("已自动切换备用数据源");
        quote.setRefreshId("r-sector");
        when(watchlistService.followSector("881121")).thenReturn(item);
        when(watchlistService.followedSectorWithQuote("881121"))
                .thenReturn(new WatchlistItemView(item, quote, null));

        mockMvc.perform(put("/api/sector-market/follows/881121"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("881121"))
                .andExpect(jsonPath("$.data.mainNetInflow").value(1200000000D))
                .andExpect(jsonPath("$.data.quoteValid").value(true))
                .andExpect(jsonPath("$.data.qualityStatus").value("FRESH_FALLBACK"))
                .andExpect(jsonPath("$.data.sourceCode").value("PYTHON_TONGHUASHUN_SECTOR"))
                .andExpect(jsonPath("$.data.refreshId").value("r-sector"));
        mockMvc.perform(delete("/api/sector-market/follows/881121"))
                .andExpect(status().isNoContent());

        verify(watchlistService).unfollowSector("881121");
    }

    private SectorMarketEntry entry() {
        SectorMarketEntry value = new SectorMarketEntry();
        value.setCode("881121");
        value.setName("半导体");
        value.setCategory(SectorCategory.INDUSTRY);
        value.setPrice(1234.5);
        value.setChangePct(2.6);
        value.setSourceRank(1);
        value.setMainNetInflow(1_200_000_000D);
        value.setLeaderStockName("中芯国际");
        return value;
    }

    private WatchlistItem sectorItem() {
        WatchlistItem value = new WatchlistItem();
        value.setId(3L);
        value.setInstrumentId(8L);
        value.setCode("881121");
        value.setName("半导体");
        value.setType("SECTOR");
        return value;
    }
}
