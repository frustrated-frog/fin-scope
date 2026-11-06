package com.finscope.common.enums.research;

import lombok.Getter;

@Getter
public enum ResearchEvidenceIssue {
    ENTITY_MISMATCH("主体不匹配"), EVENT_MISMATCH("事件不匹配"), DATE_MISSING("事件日期缺失"), DATE_MISMATCH("事件日期不符"),
    NOISY_CONTENT("正文含导航或格式噪声"), SNIPPET_ONLY("仅有搜索摘要"), QUOTE_NOT_FOUND("原文引句无法定位"),
    NUMBER_MISMATCH("数值或单位无原文支持"), SCOPE_UNKNOWN("预测或条件表述，事实口径未确认");

    private final String label;

    ResearchEvidenceIssue(String label) {
        this.label = label;
    }
}
