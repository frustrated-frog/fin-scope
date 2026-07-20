package com.finscope.common.exception;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTest {

    @Test
    void allDefaultMessagesAreChineseAndCodesAreUnique() {
        Set<String> codes = new HashSet<String>();

        for (ErrorCode value : ErrorCode.values()) {
            assertTrue(codes.add(value.getCode()), "重复错误码：" + value.getCode());
            assertFalse(value.getDefaultMessage().matches("^[\\x00-\\x7F]+$"),
                    "默认错误信息必须包含中文：" + value.name());
        }
    }
}
