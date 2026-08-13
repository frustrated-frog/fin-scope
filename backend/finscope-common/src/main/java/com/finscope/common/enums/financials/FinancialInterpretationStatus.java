package com.finscope.common.enums.financials;

/**
 * 财报解读生命周期状态。
 * <p>
 * 枚举 {@link #code()} 值与 {@code financial_interpretation.status} 字段的持久化取值保持一致，
 * 用于替换代码中散落的字符串字面量。DAO 中的 SQL 白名单为了可读仍保留字面量，
 * 但必须与本枚举同步维护。
 */
public enum FinancialInterpretationStatus {
    /** 已入队，等待异步执行器调度。 */
    QUEUED,
    /** Agent 正在执行解读。 */
    RUNNING,
    /** Agent 已产出结果，正在做结构化校验。 */
    VALIDATING,
    /** 解读成功且结果可对外展示。 */
    SUCCESS,
    /** 结果为确定性兜底，可对外展示但可信度较低。 */
    FALLBACK,
    /** 执行失败，不对外展示。 */
    FAILED;

    /** 持久化到 DB / 通过接口传输时使用的编码值。 */
    public String code() {
        return name();
    }

    /** 是否已到达终态（不再流转）。 */
    public boolean isTerminal() {
        return this == SUCCESS || this == FALLBACK || this == FAILED;
    }

    /** 是否处于进行中（未终态）。 */
    public boolean isInFlight() {
        return this == QUEUED || this == RUNNING || this == VALIDATING;
    }

    /** 是否可作为最新可展示结果对外返回。 */
    public boolean isDisplayable() {
        return this == SUCCESS || this == FALLBACK;
    }

    /** 匹配持久化字符串值，未识别时返回 null。 */
    public static FinancialInterpretationStatus fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (FinancialInterpretationStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }

    /** 判断给定字符串是否等于当前枚举的编码值。 */
    public boolean matches(String value) {
        return name().equals(value);
    }
}