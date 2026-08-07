package com.finscope.web.controller;

import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.service.quant.forecast.SingleStockForecastService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SingleStockForecastController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class SingleStockForecastControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private SingleStockForecastService service;

    @Test
    void startsAuditableSingleStockForecast() throws Exception {
        SingleStockForecast value = new SingleStockForecast();
        value.setInstrumentCode("600519.SH"); value.setAsOfDate(LocalDate.of(2026, 8, 6));
        value.setHorizonDays(20); value.setStatus("NO_OBSERVED_EDGE"); value.setBarCount(2400);
        value.setUpProbability(0.53d); value.setConclusion("未发现稳定优势");
        when(service.forecast("600519")).thenReturn(value);

        mvc.perform(post("/api/quant/single-stock-forecasts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"600519\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instrumentCode").value("600519.SH"))
                .andExpect(jsonPath("$.data.horizonDays").value(20))
                .andExpect(jsonPath("$.data.upProbability").value(0.53));
    }

    @Test
    void rejectsMalformedStockCodeBeforeCallingResearchService() throws Exception {
        mvc.perform(post("/api/quant/single-stock-forecasts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }
}
