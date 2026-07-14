package com.finscope.domain.marketintel;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalRuleExplanation {
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 规则版本。
     */
    private String ruleVersion;
    /**
     * 规则解释项列表。
     */
    private List<Item> items = Collections.emptyList();
    /**
     * 数据缺口列表。
     */
    private List<String> dataGaps = Collections.emptyList();

    public void setItems(List<Item> items) { this.items = immutable(items); }
    public void setDataGaps(List<String> dataGaps) { this.dataGaps = immutable(dataGaps); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }

    @Data
    public static class Item {
        /**
         * 级别。
         */
        private String level;
        /**
         * 文本内容。
         */
        private String text;
        /**
         * 指标引用列表。
         */
        private List<String> metricRefs = Collections.emptyList();
        public void setMetricRefs(List<String> refs) { this.metricRefs = immutable(refs); }
    }
}
