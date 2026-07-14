package com.finscope.domain.marketintel;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalRuleExplanation {
    private String summary;
    private String ruleVersion;
    private List<Item> items = Collections.emptyList();
    private List<String> dataGaps = Collections.emptyList();

    public void setItems(List<Item> items) { this.items = immutable(items); }
    public void setDataGaps(List<String> dataGaps) { this.dataGaps = immutable(dataGaps); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }

    @Data
    public static class Item {
        private String level;
        private String text;
        private List<String> metricRefs = Collections.emptyList();
        public void setMetricRefs(List<String> refs) { this.metricRefs = immutable(refs); }
    }
}
