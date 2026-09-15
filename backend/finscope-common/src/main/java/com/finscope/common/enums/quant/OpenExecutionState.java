package com.finscope.common.enums.quant;

/** Open-time execution assumptions, never inferred from closing limit status. */
public enum OpenExecutionState {
    TRADABLE,
    BUY_BLOCKED,
    SELL_BLOCKED,
    SUSPENDED
}
