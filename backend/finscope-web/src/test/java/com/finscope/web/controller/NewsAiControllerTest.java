package com.finscope.web.controller;

import com.finscope.domain.news.ai.NewsAiCallAudit;
import com.finscope.service.news.NewsAiStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NewsAiControllerTest {
    @Test
    void auditsAreReadOnlyBoundedAndDoNotExposePromptsOrCredentials() throws Exception {
        var service = mock(NewsAiStatusService.class);
        var controller = new NewsAiController();
        ReflectionTestUtils.setField(controller, "service", service);
        var call = new NewsAiCallAudit();
        call.setCallId("one");
        when(service.calls(20)).thenReturn(List.of(call));
        when(service.calls(5)).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/news/ai/calls")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].callId").value("one"))
                .andExpect(jsonPath("$.data[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].prompt").doesNotExist());
        mvc.perform(get("/api/news/ai/calls?limit=5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/api/news/ai/calls")).andExpect(status().isMethodNotAllowed());
        verify(service).calls(20);
        verify(service).calls(5);
        verifyNoMoreInteractions(service);
    }
}
