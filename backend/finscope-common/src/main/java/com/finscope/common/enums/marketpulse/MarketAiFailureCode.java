package com.finscope.common.enums.marketpulse;

/** 市场研判的本地诊断码，不包含模型原文或供应商错误正文。 */
public enum MarketAiFailureCode {
    INVALID_JSON,
    INVALID_FIELD,
    INVALID_REFERENCE,
    UNSUPPORTED_NUMBER,
    INCOMPLETE_RESPONSE,
    OUTPUT_TOO_LARGE,
    PROVIDER_RESULT_UNKNOWN,
    INTERNAL_ERROR
}
