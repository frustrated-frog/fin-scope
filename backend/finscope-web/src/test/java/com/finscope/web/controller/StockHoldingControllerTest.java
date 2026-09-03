package com.finscope.web.controller;

import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockHoldingAnalysis;
import com.finscope.domain.strategy.holding.HoldingStrategyDecision;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import com.finscope.service.strategy.holding.StockAccountService;
import com.finscope.service.strategy.holding.StockHoldingAnalysisService;
import com.finscope.service.strategy.holding.HoldingStrategyDecisionService;
import com.finscope.service.strategy.holding.StockTransactionService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockHoldingController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class StockHoldingControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private StockTransactionService transactions;
    @MockBean
    private StockAccountService accounts;
    @MockBean
    private HoldingStrategyDecisionService holdingDecisions;
    @MockBean
    private StockHoldingAnalysisService holdingAnalysis;

    @Test
    void exposesStockAccountAndCreatesBuyEvent() throws Exception {
        StockAccountSnapshot snapshot = new StockAccountSnapshot();
        when(accounts.snapshot()).thenReturn(snapshot);
        StockTransaction saved = new StockTransaction();
        saved.setId(9L);
        saved.setClientRequestId("ui-20260831-1");
        saved.setType(StockTransactionType.BUY);
        saved.setTradeDate(LocalDate.of(2026, 8, 31));
        when(transactions.create(eq("600570"), any(StockTransaction.class))).thenReturn(saved);

        mockMvc.perform(get("/api/strategy/stock-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positions").isArray());
        mockMvc.perform(post("/api/strategy/stock-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"ui-20260831-1\",\"code\":\"600570\","
                                + "\"type\":\"BUY\",\"tradeDate\":\"2026-08-31\",\"quantity\":100,"
                                + "\"price\":28.5,\"commission\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.type").value("BUY"));
    }

    @Test
    void listsTransactionsNewestFirstContract() throws Exception {
        when(transactions.list(100)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/strategy/stock-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listsAndRefreshesFrozenHoldingDecisions() throws Exception {
        HoldingStrategyDecision decision = new HoldingStrategyDecision();
        decision.setId(21L);
        decision.setInstrumentCode("600570.SH");
        decision.setAction("HOLD");
        decision.setDecisionDate(LocalDate.of(2026, 8, 31));
        when(holdingDecisions.list(100)).thenReturn(Collections.singletonList(decision));
        when(holdingDecisions.refresh()).thenReturn(Collections.singletonList(decision));

        mockMvc.perform(get("/api/strategy/holding-decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].action").value("HOLD"));
        mockMvc.perform(post("/api/strategy/holding-decisions/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].instrumentCode").value("600570.SH"));
    }

    @Test
    void reclassifiesHistoricalBuyAsOpeningBalanceWithoutDeletingLedgerHistory() throws Exception {
        StockTransaction saved = new StockTransaction();
        saved.setId(11L);
        saved.setType(StockTransactionType.OPENING_BALANCE);
        saved.setTradeDate(LocalDate.of(2026, 7, 15));
        when(transactions.reclassifyAsOpeningBalance(
                eq(9L), eq("opening-fix-9"), eq(LocalDate.of(2026, 9, 4))))
                .thenReturn(saved);

        mockMvc.perform(post("/api/strategy/stock-transactions/9/reclassify-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"opening-fix-9\","
                                + "\"tradeDate\":\"2026-09-04\",\"note\":\"历史补录\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.type").value("OPENING_BALANCE"));
    }

    @Test
    void exposesHoldingPathRiskAndFrozenForecastEvidence() throws Exception {
        StockHoldingAnalysis analysis = new StockHoldingAnalysis();
        analysis.setInstrumentCode("600570.SH");
        analysis.setInstrumentName("恒生电子");
        analysis.setEntryDate(LocalDate.of(2026, 7, 15));
        analysis.setAsOfDate(LocalDate.of(2026, 8, 31));
        analysis.setHoldingReturn(0.24d);
        analysis.setMaximumDrawdown(-0.09d);
        StockHoldingAnalysis.ForecastEvidence forecast =
                new StockHoldingAnalysis.ForecastEvidence();
        forecast.setRunId(12L);
        forecast.setUpProbability(0.61d);
        analysis.setForecast(forecast);
        when(holdingAnalysis.analyze("600570.SH")).thenReturn(analysis);

        mockMvc.perform(get("/api/strategy/stock-positions/600570.SH/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instrumentName").value("恒生电子"))
                .andExpect(jsonPath("$.data.holdingReturn").value(0.24d))
                .andExpect(jsonPath("$.data.maximumDrawdown").value(-0.09d))
                .andExpect(jsonPath("$.data.forecast.runId").value(12))
                .andExpect(jsonPath("$.data.forecast.upProbability").value(0.61d));
    }
}
