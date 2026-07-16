package com.finscope.common.exception;

public enum ErrorCode {
    SUCCESS("FS-0000", "成功", 200),

    REQUEST_PARAMETER_MISSING("FS-1001", "缺少必要的请求参数", 400),
    REQUEST_PARAMETER_INVALID("FS-1002", "请求参数不合法", 400),
    REQUEST_BODY_INVALID("FS-1003", "请求体格式错误", 400),
    REQUEST_METHOD_NOT_SUPPORTED("FS-1004", "请求方法不支持", 405),
    MEDIA_TYPE_NOT_SUPPORTED("FS-1005", "请求内容类型不支持", 415),

    UNAUTHORIZED("FS-1101", "请先登录", 401),
    FORBIDDEN("FS-1102", "无权执行该操作", 403),
    RATE_LIMITED("FS-1103", "请求过于频繁，请稍后重试", 429),

    RESOURCE_NOT_FOUND("FS-2001", "请求的资源不存在", 404),
    BUSINESS_CONFLICT("FS-2002", "当前业务状态不允许该操作", 409),
    DUPLICATE_OPERATION("FS-2003", "请勿重复操作", 409),
    DATA_VERSION_CONFLICT("FS-2004", "数据已被更新，请刷新后重试", 409),

    EXTERNAL_SERVICE_UNAVAILABLE("FS-3001", "外部服务暂不可用，请稍后重试", 502),
    EXTERNAL_SERVICE_TIMEOUT("FS-3002", "外部服务响应超时，请稍后重试", 504),
    EXTERNAL_RESPONSE_INVALID("FS-3003", "外部服务返回数据异常", 502),
    MARKET_DATA_UNAVAILABLE("FS-3004", "市场数据暂不可用，请稍后重试", 502),
    LLM_SERVICE_ERROR("FS-3005", "模型服务暂不可用，请稍后重试", 502),

    DATABASE_ERROR("FS-4001", "数据库操作失败，请稍后重试", 500),
    FILE_OPERATION_ERROR("FS-4002", "文件操作失败，请稍后重试", 500),
    ASYNC_TASK_ERROR("FS-4003", "异步任务执行失败，请稍后重试", 500),

    INTERNAL_ERROR("FS-5000", "系统繁忙，请稍后重试", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
