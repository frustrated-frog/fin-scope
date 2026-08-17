package com.finscope.common.enums.globalexpectations;

/** 预测市场预期与本地新闻现实侧活跃度的相对状态。 */
public enum ExpectationRealityState {
    EXPECTATION_LEADING("预期先行"),
    REALITY_LEADING("现实先行"),
    DUAL_ACCELERATING("双向升温"),
    QUIET("暂未共振"),
    INSUFFICIENT_DATA("数据不足");

    private final String label;

    ExpectationRealityState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
