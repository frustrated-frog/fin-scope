package com.finscope.common.enums.marketpulse;

/** 主题成员日K补齐的跨层状态契约。 */
public enum ResearchMemberReason {
    COMPLETE,
    NO_DATA,
    DATE_MISSING,
    HISTORY_GAP,
    ADJUSTMENT_REQUIRED,
    NOT_CLOSED,
    UPSTREAM_FAILED
}
