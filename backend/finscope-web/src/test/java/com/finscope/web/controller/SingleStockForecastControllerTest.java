package com.finscope.web.controller;

import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
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
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        value.setHorizonDays(5); value.setStatus("NO_CLEAR_EDGE"); value.setBarCount(2400);
        value.setUpProbability(0.53d); value.setConclusion("未发现稳定优势");
        SingleStockForecastRun run = new SingleStockForecastRun();
        run.setId(12L); run.setInstrumentCode("600519.SH"); run.setReport(value);
        when(service.forecast("600519", 5)).thenReturn(run);

        mvc.perform(post("/api/quant/single-stock-forecasts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"600519\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.report.instrumentCode").value("600519.SH"))
                .andExpect(jsonPath("$.data.report.horizonDays").value(5))
                .andExpect(jsonPath("$.data.report.upProbability").value(0.53));
    }

    @Test
    void rejectsMalformedStockCodeBeforeCallingResearchService() throws Exception {
        mvc.perform(post("/api/quant/single-stock-forecasts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnregisteredForecastHorizon() throws Exception {
        mvc.perform(post("/api/quant/single-stock-forecasts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"600519\",\"horizonDays\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAndReadsImmutableForecastRuns() throws Exception {
        SingleStockForecastRun summary = new SingleStockForecastRun();
        summary.setId(8L); summary.setInstrumentCode("603618.SH");
        summary.setStatus("CONDITIONAL"); summary.setUpProbability(0.64);
        summary.setCreatedAt(LocalDateTime.of(2026, 8, 8, 14, 0));
        when(service.history("603618", 20, null)).thenReturn(Arrays.asList(summary));
        when(service.detail(8L)).thenReturn(summary);

        mvc.perform(get("/api/quant/single-stock-forecasts?code=603618&limit=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(8))
                .andExpect(jsonPath("$.data[0].status").value("CONDITIONAL"));
        mvc.perform(get("/api/quant/single-stock-forecasts/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8));
    }

    @Test
    void exposesHealthForTheSameInstrumentHorizonAndModelVersion() throws Exception {
        ForecastModelHealth health = new ForecastModelHealth();
        health.setInstrumentCode("600519.SH");
        health.setHorizonDays(5);
        health.setModelVersion("logistic-platt-selective-v4");
        health.setStatus("HEALTHY");
        health.setSampleCount(12);
        health.setBrierScore(0.214d);
        when(service.health("600519", 5, "logistic-platt-selective-v4")).thenReturn(health);

        mvc.perform(get("/api/quant/single-stock-forecasts/health")
                        .param("code", "600519")
                        .param("horizonDays", "5")
                        .param("modelVersion", "logistic-platt-selective-v4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.sampleCount").value(12))
                .andExpect(jsonPath("$.data.brierScore").value(0.214));
    }

    @Test
    void rejectsUnregisteredHealthHorizon() throws Exception {
        when(service.health("600519", 3, "model-v1"))
                .thenThrow(new IllegalArgumentException("预测周期只支持 1、5、20 个交易日"));

        mvc.perform(get("/api/quant/single-stock-forecasts/health")
                        .param("code", "600519")
                        .param("horizonDays", "3")
                        .param("modelVersion", "model-v1"))
                .andExpect(status().isBadRequest());
    }
}
