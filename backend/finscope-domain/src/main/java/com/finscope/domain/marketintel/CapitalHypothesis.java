package com.finscope.domain.marketintel;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalHypothesis {
    /**
     * 类型。
     */
    private String type;
    /**
     * 归因或证据主张。
     */
    private String claim;
    /**
     * 置信度。
     */
    private String confidence;
    /**
     * 支撑指标引用列表。
     */
    private List<String> supportingMetricRefs = Collections.emptyList();
    /**
     * 反向证据或相反解释。
     */
    private List<String> counterEvidence = Collections.emptyList();
    /**
     * 数据缺口列表。
     */
    private List<String> dataGaps = Collections.emptyList();
    public void setSupportingMetricRefs(List<String> values) { supportingMetricRefs = immutable(values); }
    public void setCounterEvidence(List<String> values) { counterEvidence = immutable(values); }
    public void setDataGaps(List<String> values) { dataGaps = immutable(values); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
}
