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

    // 文章分类
    ARTICLE_CATEGORY_UNSUPPORTED(ErrorCode.REQUEST_PARAMETER_INVALID, "不支持的文章分类：%s"),

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
    MODEL_OUTPUT_JSON_UNPARSEABLE(ErrorCode.LLM_SERVICE_ERROR, "模型输出中没有可解析的 JSON 对象"),

    // 研究 Agent 决策与状态
    RESEARCH_AGENT_STATE_INPUT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "状态、决策和 Observation 不能为空"),
    RESEARCH_AGENT_STATE_CONFLICT(ErrorCode.DATA_VERSION_CONFLICT, "研究 Agent 状态发生并发更新，请从最新检查点恢复"),
    RESEARCH_AGENT_REPLAN_CONFLICT(ErrorCode.DATA_VERSION_CONFLICT, "研究 Agent 重规划状态发生并发更新"),
    RESEARCH_AGENT_VERIFY_CONFLICT(ErrorCode.DATA_VERSION_CONFLICT, "研究 Agent 完成校验状态发生并发更新"),
    RESEARCH_AGENT_TERMINATE_CONFLICT(ErrorCode.DATA_VERSION_CONFLICT, "研究 Agent 终止状态发生并发更新"),
    RESEARCH_DECISION_TASK_KEY_INVALID(ErrorCode.LLM_SERVICE_ERROR, "missionTaskKey 不属于服务端候选任务"),
    RESEARCH_DECISION_CONTEXT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "研究任务选择上下文不能为空"),
    RESEARCH_TASK_NOT_EXECUTABLE(ErrorCode.BUSINESS_CONFLICT, "研究任务当前不可执行：%s"),

    // 研究 Agent 工具
    RESEARCH_TOOL_ONLY_TOOL_CALL(ErrorCode.BUSINESS_CONFLICT, "只有 TOOL_CALL 决策可以进入工具调度器"),
    RESEARCH_TOOL_NO_OBSERVATION(ErrorCode.LLM_SERVICE_ERROR, "研究工具没有返回 Observation：%s"),
    RESEARCH_TOOL_ARGS_NOT_JSON(ErrorCode.LLM_SERVICE_ERROR, "持久化工具参数不是合法 JSON"),
    RESEARCH_TOOL_CONTEXT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "研究运行和决策 ID 不能为空"),
    RESEARCH_TOOL_ASSESS_NO_ARGS(ErrorCode.LLM_SERVICE_ERROR, "证据缺口评估不接受参数"),
    RESEARCH_TOOL_MATERIAL_ARGS_INVALID(ErrorCode.LLM_SERVICE_ERROR, "结构化资料检索参数必须且只能包含 stockCode、materialType 和 query"),
    RESEARCH_TOOL_MATERIAL_CODE_INVALID(ErrorCode.LLM_SERVICE_ERROR, "结构化资料检索仅支持六位 A 股代码"),
    RESEARCH_TOOL_MATERIAL_QUERY_INVALID(ErrorCode.LLM_SERVICE_ERROR, "结构化资料检索 query 未通过安全校验"),
    RESEARCH_TOOL_MATERIAL_TYPE_INVALID(ErrorCode.LLM_SERVICE_ERROR, "结构化资料检索 materialType 未通过安全校验"),
    RESEARCH_TOOL_NEWS_ARGS_INVALID(ErrorCode.LLM_SERVICE_ERROR, "公开资料搜索参数必须且只能包含 query 和 intent"),
    RESEARCH_TOOL_NEWS_QUERY_INVALID(ErrorCode.LLM_SERVICE_ERROR, "公开资料搜索 query 未通过安全校验"),
    RESEARCH_TOOL_NEWS_INTENT_INVALID(ErrorCode.LLM_SERVICE_ERROR, "公开资料搜索 intent 未通过安全校验"),
    SEARCH_PROVIDERS_ALL_UNAVAILABLE(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "所有搜索供应商均不可用"),

    // 研究规划与任务图
    RESEARCH_PLAN_ENRICH_NOT_OBJECT(ErrorCode.LLM_SERVICE_ERROR, "计划增强必须是 JSON 对象"),
    RESEARCH_PLAN_ENRICH_EMPTY(ErrorCode.LLM_SERVICE_ERROR, "模型未返回可应用的计划增强字段"),
    RESEARCH_PLAN_SUBJECT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "研究命题和研究对象不能为空"),
    RESEARCH_PLAN_ACTION_BUDGET_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "研究动作预算不合法"),
    RESEARCH_PLAN_FIELD_NOT_STRING(ErrorCode.LLM_SERVICE_ERROR, "%s 必须是字符串"),
    RESEARCH_TASK_START_FAILED(ErrorCode.BUSINESS_CONFLICT, "研究任务无法开始：%s"),
    RESEARCH_TASK_COMPLETE_FAILED(ErrorCode.BUSINESS_CONFLICT, "研究任务无法完成：%s"),
    RESEARCH_PATCH_ONLY_PENDING(ErrorCode.BUSINESS_CONFLICT, "只允许增加或替换未完成的局部研究任务"),
    RESEARCH_PATCH_KEY_PREFIX(ErrorCode.REQUEST_PARAMETER_INVALID, "局部任务编码必须使用 adaptive_ 前缀"),
    RESEARCH_PATCH_TOOL_NEWS_ONLY(ErrorCode.REQUEST_PARAMETER_INVALID, "局部重规划只能使用公开新闻搜索工具"),
    RESEARCH_PATCH_INTENT_NOT_ALLOWED(ErrorCode.REQUEST_PARAMETER_INVALID, "局部重规划意图不在白名单中"),
    RESEARCH_PATCH_FIELD_UNSAFE(ErrorCode.REQUEST_PARAMETER_INVALID, "局部重规划任务字段未通过安全校验"),
    RESEARCH_PATCH_TASK_LOCKED(ErrorCode.BUSINESS_CONFLICT, "局部任务已经执行或正在执行，不能被重写：%s"),
    RESEARCH_MISSION_FINALIZE_FAILED(ErrorCode.BUSINESS_CONFLICT, "研究任务图无法进入终态：%s"),
    RESEARCH_MISSION_TASK_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND, "研究任务不存在：%s"),
    RESEARCH_GAP_INPUT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "研究运行和证据评估不能为空"),
    RESEARCH_SOURCE_TASK_INVALID(ErrorCode.BUSINESS_CONFLICT, "只有已校验的公开新闻搜索任务可以生成临时来源"),
    RESEARCH_SOURCE_QUERY_UNSAFE(ErrorCode.REQUEST_PARAMETER_INVALID, "研究搜索词未通过安全校验"),
    RESEARCH_MISSION_STAGE_DUPLICATE(ErrorCode.DATA_INTEGRITY_ERROR, "研究任务图包含重复系统阶段：%s"),
    RESEARCH_MISSION_STAGE_MISSING(ErrorCode.DATA_INTEGRITY_ERROR, "研究任务图缺少系统阶段：%s"),
    RESEARCH_QUERY_ROUND_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "研究查询轮次必须介于 1 到 3 之间"),
    EVIDENCE_SUFFICIENCY_COUNT_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "证据充分性计数不合法"),
    RESEARCH_THESIS_REQUIRED_FOR_REPORT(ErrorCode.REQUEST_PARAMETER_INVALID, "生成命题报告需要提供研究命题"),
    EVIDENCE_CHUNK_PARAM_INVALID(ErrorCode.REQUEST_PARAMETER_INVALID, "证据分块参数无效"),
    BENCHMARK_INPUT_REQUIRED(ErrorCode.REQUEST_PARAMETER_INVALID, "Benchmark 输入不能为空");

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