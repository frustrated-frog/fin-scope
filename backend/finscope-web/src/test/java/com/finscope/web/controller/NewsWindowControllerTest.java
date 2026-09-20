package com.finscope.web.controller;

import com.finscope.service.news.NewsFeedSnapshot;
import com.finscope.service.news.NewsWindowService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NewsWindowController.class)
@Import(FinScopeProperties.class)
class NewsWindowControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private NewsWindowService news;

    @Test
    void passesFiltersAndAnchorAndReturnsPaginationMetadata() throws Exception {
        LocalDateTime anchor = LocalDateTime.of(2026, 9, 21, 10, 0);
        NewsFeedSnapshot result = new NewsFeedSnapshot(Collections.emptyList(), Collections.emptyList(), anchor, 1);
        result.setTotalCount(151);
        result.setPage(2);
        result.setTotalPages(4);
        result.setAsOf(anchor);
        when(news.load("COMPANY", "CLS", "芯片", 24, 2, 50, anchor)).thenReturn(result);
        mvc.perform(get("/api/news/paged").param("category", "COMPANY").param("source", "CLS")
                        .param("query", "芯片").param("hours", "24").param("page", "2")
                        .param("asOf", "2026-09-21T10:00:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(151))
                .andExpect(jsonPath("$.data.page").value(2)).andExpect(jsonPath("$.data.totalPages").value(4));
        verify(news).load("COMPANY", "CLS", "芯片", 24, 2, 50, anchor);
    }
}
