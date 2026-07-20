package com.finscope.common.api;

import com.finscope.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void buildsChineseSuccessEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ok", "trace-1");

        assertTrue(response.isSuccess());
        assertEquals("FS-0000", response.getCode());
        assertEquals("成功", response.getMessage());
        assertEquals("ok", response.getData());
        assertEquals("trace-1", response.getTraceId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void buildsFailureEnvelopeWithoutBusinessData() {
        ApiResponse<String> response = ApiResponse.failure(
                ErrorCode.RESOURCE_NOT_FOUND, "主题不存在", "trace-2");

        assertFalse(response.isSuccess());
        assertEquals("FS-2001", response.getCode());
        assertEquals("主题不存在", response.getMessage());
        assertNull(response.getData());
        assertEquals("trace-2", response.getTraceId());
        assertNotNull(response.getTimestamp());
    }
}
