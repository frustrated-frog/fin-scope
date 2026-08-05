package com.finscope.common.exception;

/**
 * 业务异常码。
 *
 * <p>{@link ErrorCode} 承载通用的、跨场景复用的错误语义（如“请求参数不合法”），
 * 而本枚举承载具体业务场景下的可读报错信息（如“研究运行不能为空”）。
 * 抛出异常时应直接引用本枚举，避免在代码中手写报错字符串。</p>
 *
 * <p>每个业务码关联一个通用 {@link ErrorCode}，用于复用其对外错误码和 HTTP 状态，
 * 从而无需改动统一响应与异常处理链路。</p>
 */
public enum BizErrorCode {
    // 研究任务图
    RESEARCH_RUN_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "研究运行不能为空"),

    // URL 摄入
    URL_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "URL 不能为空"),
    URL_SCHEME_UNSUPPORTED(ErrorCode.REQUEST_PARAMETER_INVALID, "仅支持 HTTP 或 HTTPS URL"),
    URL_MALFORMED(ErrorCode.REQUEST_PARAMETER_INVALID, "URL 格式不正确：%s"),

    // 量化策略
    STRATEGY_PROMPT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "策略描述不能为空"),

    // 研究雷达
    RADAR_EVENT_STATE_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "雷达事件处理状态不合法"),

    // 模型输出解析
    MODEL_OUTPUT_EMPTY(ErrorCode.LLM_SERVICE_ERROR, "模型返回空内容"),
    MODEL_OUTPUT_TOO_LONG(ErrorCode.LLM_SERVICE_ERROR, "模型输出超过字符上限"),
    MODEL_OUTPUT_NO_JSON(ErrorCode.LLM_SERVICE_ERROR, "模型输出中没有 JSON 对象"),
    MODEL_OUTPUT_JSON_INCOMPLETE(ErrorCode.LLM_SERVICE_ERROR, "模型输出中的 JSON 对象不完整"),
    MODEL_OUTPUT_JSON_UNPARSEABLE(ErrorCode.LLM_SERVICE_ERROR, "模型输出中没有可解析的 JSON 对象");

    private final ErrorCode errorCode;
    private final String message;

    BizErrorCode(ErrorCode errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * 关联的通用错误码，提供对外错误码与 HTTP 状态。
     *
     * @return 关联的 {@link ErrorCode}。
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 业务报错信息。
     *
     * @return 具体的可读报错信息。
     */
    public String getMessage() {
        return message;
    }

    /**
     * 使用参数渲染报错信息中的占位符。
     *
     * @param args 占位符实参。
     * @return 渲染后的报错信息。
     */
    public String format(Object... args) {
        return String.format(message, args);
    }
}