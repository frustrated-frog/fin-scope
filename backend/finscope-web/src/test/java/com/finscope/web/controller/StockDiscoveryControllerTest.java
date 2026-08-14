package com.finscope.web.controller;

import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.service.quant.discovery.StockDiscoveryService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockDiscoveryController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class StockDiscoveryControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private StockDiscoveryService service;

    @Test
    void exposesNextAutomaticScheduleEvenBeforeTheFirstRun() throws Exception {
        when(service.history(1)).thenReturn(Collections.emptyList());

        mvc.perform(get("/api/quant/stock-discoveries/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EMPTY"))
                .andExpect(jsonPath("$.data.nextScheduledAt").isString());
    }

    @Test
    void distinguishesDeliveredTaskFromFailedBusinessCalculation() throws Exception {
        StockDiscoveryRun run = new StockDiscoveryRun();
        run.setId(7L);
        run.setBusinessDate(LocalDate.of(2026, 8, 14));
        run.setStatus("FAILED");
        run.setStartedAt(LocalDateTime.of(2026, 8, 14, 15, 31));
        run.setErrorMessage("所有热门板块数据源不可用");
        when(service.history(1)).thenReturn(List.of(run));

        mvc.perform(get("/api/quant/stock-discoveries/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data.businessStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.retryPending").value(true))
                .andExpect(jsonPath("$.data.errorMessage").value("所有热门板块数据源不可用"));
    }
}
