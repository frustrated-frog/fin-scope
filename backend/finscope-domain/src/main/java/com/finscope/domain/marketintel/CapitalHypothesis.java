package com.finscope.domain.marketintel;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalHypothesis {
    private String type;
    private String claim;
    private String confidence;
    private List<String> supportingMetricRefs = Collections.emptyList();
    private List<String> counterEvidence = Collections.emptyList();
    private List<String> dataGaps = Collections.emptyList();
    public void setSupportingMetricRefs(List<String> values) { supportingMetricRefs = immutable(values); }
    public void setCounterEvidence(List<String> values) { counterEvidence = immutable(values); }
    public void setDataGaps(List<String> values) { dataGaps = immutable(values); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
}
