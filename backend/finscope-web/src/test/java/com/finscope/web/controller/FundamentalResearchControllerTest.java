package com.finscope.web.controller;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.domain.fundamentals.FundamentalResearchWorkspace;
import com.finscope.service.fundamentals.FundamentalResearchService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FundamentalResearchController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class FundamentalResearchControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private FundamentalResearchService service;

    @Test
    void workspaceReadAndExplicitRefreshHaveSeparateRoutes() throws Exception {
        var workspace = new FundamentalResearchWorkspace();
        workspace.setCode("603618");
        when(service.view("603618")).thenReturn(workspace);
        mvc.perform(get("/api/watchlist/stocks/603618/fundamental-research"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("603618"));
        verify(service, never()).refresh(anyString());
    }

    @Test
    void invalidInputsCannotReachPersistence() throws Exception {
        mvc.perform(put("/api/watchlist/stocks/603618/fundamental-research/notes")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sourceUrl\":\"javascript:alert(1)\",\"revision\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/watchlist/stocks/603618/fundamental-research/watches")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/watchlist/stocks/603618/fundamental-research/watches/1/review")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUPPORTED\",\"note\":\"  \",\"revision\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void concurrentNotesUpdateReturnsConflictForReloadInsteadOfOverwriting() throws Exception {
        when(service.saveNotes(eq("603618"), any())).thenThrow(new BusinessConflictException("经营底稿已更新"));
        mvc.perform(put("/api/watchlist/stocks/603618/fundamental-research/notes")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revenueModel\":\"电缆销售\",\"revision\":1}"))
                .andExpect(status().isConflict());
    }
}
