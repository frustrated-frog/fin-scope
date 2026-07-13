package com.finscope.web.controller;

import com.finscope.service.research.LearningTaskService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LearningTaskController.class)
@Import({FinScopeProperties.class, CorsConfig.class})
class LearningTaskControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private LearningTaskService learningTaskService;

    @Test
    void rejectsLegacyDirectStatusMutation() throws Exception {
        mockMvc.perform(post("/api/learning-tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
    }
}
