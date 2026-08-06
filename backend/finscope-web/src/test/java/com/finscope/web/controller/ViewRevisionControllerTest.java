package com.finscope.web.controller;

import com.finscope.service.cache.ViewRevision;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.sse.ViewRevisionSseRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViewRevisionController.class)
@Import(FinScopeProperties.class)
class ViewRevisionControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private ViewRevisionService revisions;

    @MockBean
    private ViewRevisionSseRegistry stream;

    @Test
    void returnsCurrentVersionsForFallbackReconciliation() throws Exception {
        when(revisions.current(anyList())).thenReturn(Arrays.asList(
                new ViewRevision("news", 3L, LocalDateTime.of(2026, 8, 6, 10, 0)),
                new ViewRevision("radar", 4L, LocalDateTime.of(2026, 8, 6, 10, 0))));

        mvc.perform(get("/api/view-revisions").param("scopes", "news,radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].scope").value("news"))
                .andExpect(jsonPath("$.data[1].revision").value(4));
    }
}
