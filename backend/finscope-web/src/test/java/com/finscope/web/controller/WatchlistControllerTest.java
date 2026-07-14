package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.service.instrument.WatchlistService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import(FinScopeProperties.class)
class WatchlistControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private WatchlistService watchlistService;

    @Test
    void listsOnlyInvestmentItemsThroughTypedService() throws Exception {
        when(watchlistService.listInvestmentItemsWithQuotes(false)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(watchlistService).listInvestmentItemsWithQuotes(false);
    }

    @Test
    void rejectsSectorOnOrdinaryWatchlistEndpoint() throws Exception {
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST, "板块请使用板块关注接口"))
                .when(watchlistService).addInvestment("BK1036", "SECTOR", null);

        mockMvc.perform(post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BK1036\",\"type\":\"SECTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("板块请使用板块关注接口"));
    }
}
