package com.finscope.common.enums.research;

import lombok.Getter;

@Getter
public enum ResearchValueRelation {
    EXACT(""), GREATER_THAN("超过"), AT_LEAST("至少"), LESS_THAN("低于"), AT_MOST("不超过"), APPROXIMATE("约");

    private final String label;

    ResearchValueRelation(String label) {
        this.label = label;
    }
}
