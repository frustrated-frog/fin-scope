package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.fundamentals.FundamentalBusinessResearch;
import com.finscope.service.fundamentals.FundamentalBusinessResearchService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FundamentalBusinessResearchController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class FundamentalBusinessResearchControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private FundamentalBusinessResearchService service;
    private static final String BASE = "/api/watchlist/stocks/603618/fundamental-research/business-research";

    @Test
    void readingBusinessMaterialNeverStartsAResearchRun() throws Exception {
        when(service.view("603618")).thenReturn(new FundamentalBusinessResearch());
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IDLE"))
                .andExpect(jsonPath("$.data.findings").isArray());
        verify(service, never()).start(anyString());
    }

    @Test
    void startsOnlyThroughExplicitPostAndReturnsARecoverableModelError() throws Exception {
        when(service.start("603618")).thenThrow(new BusinessException(ErrorCode.LLM_SERVICE_ERROR, "模型暂不可用"));
        mvc.perform(post(BASE + "/runs")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("FS-3005"));
    }
}
