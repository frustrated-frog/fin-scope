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
    RESEARCH_RUN_AND_THESIS_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "研究运行和命题不能为空"),

    // URL 摄入
    URL_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "URL 不能为空"),
    URL_SCHEME_UNSUPPORTED(ErrorCode.REQUEST_PARAMETER_INVALID, "仅支持 HTTP 或 HTTPS URL"),
    URL_MALFORMED(ErrorCode.REQUEST_PARAMETER_INVALID, "URL 格式不正确：%s"),
    URL_CONTENT_DYNAMIC_SHELL(ErrorCode.REQUEST_PARAMETER_INVALID,
            "未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页，无法生成可靠情报卡片。URL: %s"),
    URL_CONTENT_LINK_ONLY(ErrorCode.REQUEST_PARAMETER_INVALID,
            "未能读取到可用正文：检测到正文仅包含 X Article 链接，缺少实际内容。URL: %s。该推文可能包含长文内容，建议稍后重试或直接访问原链接。"),
    URL_CONTENT_TOO_SHORT(ErrorCode.REQUEST_PARAMETER_INVALID,
            "未能读取到可用正文：页面正文过短，无法生成可靠情报卡片。URL: %s"),

    // 市场情报
    DRAGON_TIGER_DAYS_UNSUPPORTED(ErrorCode.REQUEST_PARAMETER_INVALID, "龙虎榜查询天数仅支持 30、60、120"),

    // 量化策略
    STRATEGY_PROMPT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "策略描述不能为空"),

    // 研究雷达
    RADAR_EVENT_STATE_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "雷达事件处理状态不合法"),
    RADAR_OBSERVATION_STATE_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "观察项状态不合法"),
    RADAR_OBSERVATION_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND, "观察项不存在"),
    RADAR_EVENT_ID_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "雷达事件不能为空"),
    RADAR_OBSERVATION_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "观察项不能为空"),
    RADAR_OBSERVATION_TOO_LONG(ErrorCode.REQUEST_PARAMETER_INVALID, "观察项不能超过300字"),
    RADAR_OBSERVATION_SYSTEM_UNDELETABLE(ErrorCode.BUSINESS_CONFLICT, "系统观察项不能删除"),

    // 分页与查询参数
    PAGE_REQUEST_INVALID_LEARNING_TASK(ErrorCode.REQUEST_PARAMETER_INVALID, "分页参数不合法"),
    PAGE_REQUEST_INVALID_KNOWLEDGE(ErrorCode.REQUEST_PARAMETER_INVALID, "知识分页参数不合法"),
    PAGE_REQUEST_INVALID_KNOWLEDGE_ENTRY(ErrorCode.REQUEST_PARAMETER_INVALID, "知识条目分页参数不合法"),
    CONTEXT_LIMIT_INVALID_EVIDENCE(ErrorCode.REQUEST_PARAMETER_INVALID, "证据上下文数量限制不合法"),
    CONTEXT_LIMIT_INVALID_EVENT(ErrorCode.REQUEST_PARAMETER_INVALID, "事件上下文数量限制不合法"),
    PROJECTION_BATCH_SIZE_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "投影恢复批量大小不合法"),

    // 资金流查询参数
    CAPITAL_FLOW_INSTRUMENT_IDS_NULL(ErrorCode.REQUEST_PARAMETER_INVALID, "标的 ID 列表不能包含空值"),
    CAPITAL_FLOW_RANGE_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "起始时间、结束时间和快照时点均为必填"),
    CAPITAL_FLOW_RANGE_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "起始时间不能晚于结束时间"),

    // Agent 学习任务
    AGENT_SUGGESTION_KEYS_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "Agent 建议缺少 eventId 或 taskKey"),

    // 资源不存在
    RESEARCH_MISSION_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND, "研究任务图不存在：%s"),
    QUANT_DATA_SYNC_RUN_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND, "进行中的量化数据同步任务不存在：%s"),
    MARKET_DATA_REFRESH_RUN_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND, "市场数据刷新任务不存在：%s"),

    // 业务状态冲突
    QUANT_EXPERIMENT_STATE_CHANGED(ErrorCode.BUSINESS_CONFLICT, "实验状态已变化，拒绝写入不一致结果：%s"),

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