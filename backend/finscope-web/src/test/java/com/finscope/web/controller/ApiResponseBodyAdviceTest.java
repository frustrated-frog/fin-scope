package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.config.RequestLoggingFilter;
import com.finscope.web.handler.ApiResponseBodyAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiResponseBodyAdviceTest {
    private MockMvc mockMvc;
    private ApiResponseBodyAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new ApiResponseBodyAdvice();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(advice)
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    void wrapsOrdinaryJsonResponses() throws Exception {
        mockMvc.perform(get("/test/value")
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("FS-0000"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.name").value("value"))
                .andExpect(jsonPath("$.traceId").value("trace-success"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void preservesResponseEntityStatusAndHeaders() throws Exception {
        mockMvc.perform(post("/test/created"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/test/value/7"))
                .andExpect(jsonPath("$.data.name").value("created"));
    }

    @Test
    void doesNotWrapAnExistingEnvelopeTwice() throws Exception {
        mockMvc.perform(get("/test/envelope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("wrapped"))
                .andExpect(jsonPath("$.data.data").doesNotExist());
    }

    @Test
    void leavesNoContentResponsesEmpty() throws Exception {
        mockMvc.perform(delete("/test/no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void excludesSseEmitterReturnTypes() throws Exception {
        MethodParameter returnType = new MethodParameter(
                TestController.class.getDeclaredMethod("stream"), -1);

        assertFalse(advice.supports(returnType, null));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {
        @GetMapping(value = "/value", produces = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> value() {
            return Collections.singletonMap("name", "value");
        }

        @PostMapping("/created")
        ResponseEntity<Map<String, String>> created() {
            return ResponseEntity.created(URI.create("/test/value/7"))
                    .body(Collections.singletonMap("name", "created"));
        }

        @GetMapping("/envelope")
        ApiResponse<Map<String, String>> envelope() {
            return ApiResponse.success(Collections.singletonMap("name", "wrapped"), "trace-wrapped");
        }

        @DeleteMapping("/no-content")
        ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            return new SseEmitter();
        }
    }
}
