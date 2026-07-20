package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.config.RequestLoggingFilter;
import com.finscope.web.response.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerResponseProtocolTest {

    private MockMvc mockMvc;
    private TestController controller;

    @BeforeEach
    void setUp() {
        controller = new TestController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    void returnsOneExplicitEnvelopeWithTheRequestTraceId() throws Exception {
        mockMvc.perform(get("/test/value")
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-success"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-success"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("FS-0000"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.name").value("value"))
                .andExpect(jsonPath("$.data.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").value("trace-success"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void preservesCreatedAndAcceptedStatusesAndLocationHeaders() throws Exception {
        mockMvc.perform(post("/test/created"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/test/value/7"))
                .andExpect(jsonPath("$.data.name").value("created"));

        mockMvc.perform(post("/test/accepted"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.name").value("accepted"));
    }

    @Test
    void leavesNoContentResponsesEmpty() throws Exception {
        mockMvc.perform(delete("/test/no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void keepsSseAsAnAsyncEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping(value = "/value", produces = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<Map<String, String>> value() {
            return ApiResponses.success(Collections.singletonMap("name", "value"));
        }

        @PostMapping("/created")
        ResponseEntity<ApiResponse<Map<String, String>>> created() {
            return ResponseEntity.created(URI.create("/test/value/7"))
                    .body(ApiResponses.success(Collections.singletonMap("name", "created")));
        }

        @PostMapping("/accepted")
        ResponseEntity<ApiResponse<Map<String, String>>> accepted() {
            return ResponseEntity.accepted()
                    .body(ApiResponses.success(Collections.singletonMap("name", "accepted")));
        }

        @DeleteMapping("/no-content")
        ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() throws IOException {
            SseEmitter emitter = new SseEmitter();
            emitter.send(SseEmitter.event().name("ready").data("ready"));
            emitter.complete();
            return emitter;
        }
    }
}
