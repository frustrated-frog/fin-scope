package com.finscope.web.controller;

import com.finscope.domain.strategy.StrategyHolding;
import com.finscope.domain.strategy.StrategyStockThesis;
import com.finscope.service.strategy.StrategyHoldingService;
import com.finscope.service.strategy.StrategyPlaybookService;
import com.finscope.service.strategy.StrategyReviewService;
import com.finscope.service.strategy.StrategyStockThesisService;
import com.finscope.web.handler.ApiExceptionHandler;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class StrategyControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private StrategyHoldingService holdingService;
    @MockBean private StrategyPlaybookService playbookService;
    @MockBean private StrategyStockThesisService thesisService;
    @MockBean private StrategyReviewService reviewService;

    @Test
    void getsEmptyWorkspace() throws Exception {
        when(holdingService.list()).thenReturn(Collections.emptyList());
        when(playbookService.list()).thenReturn(Collections.emptyList());
        when(thesisService.list()).thenReturn(Collections.emptyList());
        when(reviewService.list()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/strategy/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdings").isArray()).andExpect(jsonPath("$.data.targetWeight").value(0));
    }

    @Test
    void createsHoldingWithRevision() throws Exception {
        StrategyHolding value=new StrategyHolding();value.setId(1L);value.setCode("020608");value.setName("测试基金");value.setType("FUND");value.setRole("CORE");value.setTargetWeight(60);value.setRevision(0);
        when(holdingService.add(anyString(),anyString(),anyString(),anyDouble(),anyDouble(),anyString())).thenReturn(value);
        mockMvc.perform(post("/api/strategy/holdings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"020608\",\"type\":\"FUND\",\"role\":\"CORE\",\"targetWeight\":60,\"currentWeight\":0,\"note\":\"长期核心\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("020608"))
                .andExpect(jsonPath("$.data.revision").value(0));
    }

    @Test
    void allowsPatchFromLocalFrontend() throws Exception {
        StrategyStockThesis value = new StrategyStockThesis();
        value.setId(1L);
        value.setStage("WATCH_POOL");
        when(thesisService.update(org.mockito.ArgumentMatchers.eq(1L), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(value);

        mockMvc.perform(patch("/api/strategy/stock-theses/1")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"WATCH_POOL\",\"thesis\":\"逻辑\",\"buyConditions\":\"买入\","
                                + "\"invalidationConditions\":\"失效\",\"watchFocus\":\"观察\",\"note\":\"\",\"revision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("WATCH_POOL"));
    }
}
