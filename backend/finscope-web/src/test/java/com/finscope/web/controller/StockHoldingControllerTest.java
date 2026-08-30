package com.finscope.web.controller;

import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import com.finscope.service.strategy.holding.StockAccountService;
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
}
