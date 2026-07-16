package com.finscope.web.controller;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.web.config.RequestLoggingFilter;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    void mapsTypedBusinessExceptionsToStableChineseEnvelope() throws Exception {
        mockMvc.perform(get("/errors/not-found")
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FS-2001"))
                .andExpect(jsonPath("$.message").value("主题不存在"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").value("trace-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FS-2002"))
                .andExpect(jsonPath("$.message").value("当前状态不允许重复提交"));
    }

    @Test
    void mapsMissingAndInvalidParameters() throws Exception {
        mockMvc.perform(get("/errors/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1001"))
                .andExpect(jsonPath("$.message").value("缺少必要的请求参数"));

        mockMvc.perform(get("/errors/parameter").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    void mapsInvalidRequestBodiesAndBeanValidation() throws Exception {
        mockMvc.perform(post("/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1003"))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));

        mockMvc.perform(post("/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"))
                .andExpect(jsonPath("$.message").value("name：名称不能为空"));
    }

    @Test
    void mapsUnsupportedMethodAndMediaType() throws Exception {
        mockMvc.perform(put("/errors/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("FS-1004"));

        mockMvc.perform(post("/errors/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=value"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FS-1005"));
    }

    @Test
    void hidesInfrastructureAndUnknownExceptionDetails() throws Exception {
        mockMvc.perform(get("/errors/database"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FS-4001"))
                .andExpect(jsonPath("$.message").value("数据库操作失败，请稍后重试"));

        mockMvc.perform(get("/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FS-5000"))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @RestController
    @RequestMapping("/errors")
    static class ErrorController {
        @GetMapping("/not-found")
        Object notFound() {
            throw new ResourceNotFoundException("主题不存在");
        }

        @GetMapping("/conflict")
        Object conflict() {
            throw new BusinessConflictException("当前状态不允许重复提交");
        }

        @GetMapping("/parameter")
        Object parameter(@RequestParam Integer count) {
            return count;
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Object body(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping("/database")
        Object database() {
            throw new DataRetrievalFailureException("sqlite table missing");
        }

        @GetMapping("/unexpected")
        Object unexpected() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    static class TestRequest {
        @NotBlank(message = "名称不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
