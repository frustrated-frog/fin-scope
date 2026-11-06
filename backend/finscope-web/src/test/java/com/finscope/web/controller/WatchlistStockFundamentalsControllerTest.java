package com.finscope.web.controller;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.domain.valuation.StockValuationPeer;
import com.finscope.service.instrument.StockFundamentalsService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistStockFundamentalsController.class)
@Import(FinScopeProperties.class)
class WatchlistStockFundamentalsControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private StockFundamentalsService service;

    @Test
    void defaultsToAutomaticLoadAndMapsQuarterEvidenceWithRealValuationHistory() throws Exception {
        StockFundamentals source = snapshot();
        StockValuationSnapshot valuation = new StockValuationSnapshot();
        valuation.setObservedDate(LocalDate.of(2026, 11, 6));
        valuation.setPeTtm(new BigDecimal("18.37"));
        valuation.setSourceCode("EXISTING_PROVIDER");
        StockValuationView view = new StockValuationView();
        view.setLatest(valuation);
        view.setHistory(List.of(valuation));
        source.setValuation(view);
        StockValuationPeer peer = new StockValuationPeer();
        peer.setCode("600809");
        peer.setName("山西汾酒");
        peer.setObservedDate(LocalDate.of(2026, 11, 6));
        peer.setSourceCode("EXISTING_PROVIDER");
        peer.setQualityStatus("FRESH_PRIMARY");
        peer.setPeTtm(new BigDecimal("22.5"));
        source.setPeers(List.of(peer));
        source.setPeerComparisonNote("本地同行样本，不代表全行业估值");
        when(service.load("600519", false)).thenReturn(source);

        mvc.perform(get("/api/watchlist/stocks/600519/fundamentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instrument.id").value(17))
                .andExpect(jsonPath("$.data.instrument.code").value("600519"))
                .andExpect(jsonPath("$.data.instrument.type").value("STOCK"))
                .andExpect(jsonPath("$.data.instrument.aliases").doesNotExist())
                .andExpect(jsonPath("$.data.quarters[0].periodEnd").value("2026-09-30"))
                .andExpect(jsonPath("$.data.quarters[0].reportType").value("Q3"))
                .andExpect(jsonPath("$.data.quarters[0].reportId").value(81))
                .andExpect(jsonPath("$.data.quarters[0].currency").value("CNY"))
                .andExpect(jsonPath("$.data.quarters[0].revenue").value(123456789.25))
                .andExpect(jsonPath("$.data.quarters[0].netProfitParent").value(-500.5))
                .andExpect(jsonPath("$.data.quarters[0].sourceCode").value("EXISTING_PROVIDER"))
                .andExpect(jsonPath("$.data.quarters[0].qualityStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.quarters[0].publishedAt").value("2026-10-28T00:00:00"))
                .andExpect(jsonPath("$.data.quarters[0].dataGaps[0]").value("缺少同口径营业成本"))
                .andExpect(jsonPath("$.data.valuation.history[0].observedDate").value("2026-11-06"))
                .andExpect(jsonPath("$.data.valuation.history[0].peTtm").value(18.37))
                .andExpect(jsonPath("$.data.peers[0].code").value("600809"))
                .andExpect(jsonPath("$.data.peers[0].observedDate").value("2026-11-06"))
                .andExpect(jsonPath("$.data.peers[0].sourceCode").value("EXISTING_PROVIDER"))
                .andExpect(jsonPath("$.data.peers[0].peTtm").value(22.5))
                .andExpect(jsonPath("$.data.peers[0].pbMrq").isEmpty())
                .andExpect(jsonPath("$.data.peerComparisonNote").value("本地同行样本，不代表全行业估值"))
                .andExpect(jsonPath("$.data.refreshedAt").value("2026-11-06T02:00:00Z"))
                .andExpect(jsonPath("$.data.warnings[0]").value("历史样本积累中"));
        verify(service).load("600519", false);
    }

    @Test
    void forwardsExplicitRefreshAndPreservesUnavailableValuesAsNull() throws Exception {
        when(service.load("600519", true)).thenReturn(snapshot());
        mvc.perform(get("/api/watchlist/stocks/600519/fundamentals?refresh=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valuation").isEmpty())
                .andExpect(jsonPath("$.data.quarters[0].operatingCashFlow").isEmpty())
                .andExpect(jsonPath("$.data.quarters[0].grossMarginPct").isEmpty());
        verify(service).load("600519", true);
    }

    @Test
    void delegatesInvalidCodeToExistingUnifiedBusinessErrorMapping() throws Exception {
        when(service.load("fund", false)).thenThrow(new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID));
        mvc.perform(get("/api/watchlist/stocks/fund/fundamentals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"));
    }

    private static StockFundamentals snapshot() {
        Instrument stock = new Instrument();
        stock.setId(17L);
        stock.setCode("600519");
        stock.setName("贵州茅台");
        stock.setMarket("SH");
        stock.setType("STOCK");
        stock.setAliases("internal-persistence-field");
        StockFinancialQuarter quarter = new StockFinancialQuarter();
        quarter.setPeriodEnd(LocalDate.of(2026, 9, 30));
        quarter.setReportType(FinancialReportType.Q3);
        quarter.setReportId(81L);
        quarter.setCurrency("CNY");
        quarter.setSourceCode("EXISTING_PROVIDER");
        quarter.setQualityStatus(FinancialQualityStatus.PARTIAL);
        quarter.setPublishedAt(LocalDateTime.of(2026, 10, 28, 0, 0));
        quarter.setRevenue(new BigDecimal("123456789.25"));
        quarter.setNetProfitParent(new BigDecimal("-500.50"));
        quarter.getDataGaps().add("缺少同口径营业成本");
        StockFundamentals source = new StockFundamentals();
        source.setInstrument(stock);
        source.setQuarters(List.of(quarter));
        source.setRefreshedAt(Instant.parse("2026-11-06T02:00:00Z"));
        source.getWarnings().add("历史样本积累中");
        return source;
    }
}
